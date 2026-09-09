"""P2 qualification analysis: paired fork ratios -> geomean + bootstrap 95% CI.

Usage: python analyze_qualification.py [--runs DIR] [--forks N]
Groups screen CSVs by (variant, fork), takes per-workload medians per fork, pairs B0/V forks,
reports per-workload: n forks, geomean ratio (B0/V, >1 = V faster), bootstrap 95% CI, median
ratio, CV of fork ratios, oracle agreement. Equivalence band +/-5% applied by the reader.
"""
import argparse
import csv
import glob
import math
import os
import random
import statistics
from collections import defaultdict

ap = argparse.ArgumentParser()
ap.add_argument("--runs", default="D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-timing-screen-v1")
ap.add_argument("--forks", type=int, default=None)
ap.add_argument("--boot", type=int, default=10000)
ap.add_argument("--seed", type=int, default=20260908)
args = ap.parse_args()

# variant,fork -> workload -> list of elapsed
cell = defaultdict(lambda: defaultdict(list))
oracle = defaultdict(dict)
tracked = defaultdict(dict)
for path in glob.glob(os.path.join(args.runs, "screen-*.csv")):
    base = os.path.basename(path)
    # screen-{variant}-fork{fork}-{stamp}.csv ; pre-rig files without a fork label are ignored
    if "-fork" not in base:
        continue
    try:
        rest = base[len("screen-"):-len(".csv")]
        variant, rest2 = rest.split("-", 1)
        fork = rest2.split("-", 1)[0].replace("fork", "")
    except ValueError:
        continue
    if fork == "0":
        continue  # superseded seed-1 trial runs; qualification uses forks 1..N
    for r in csv.DictReader(open(path)):
        if r.get("variant", variant) != variant:
            continue
        if r.get("fork", fork) == "0":
            continue  # superseded single-JVM screening runs; qualification uses forks 1..N
        cell[(variant, fork)][r["workload"]].append(int(r["elapsed_ns"]))
        oracle[(variant, fork, r["workload"])] = (r["rows"], r["hash"])
        tracked[(variant, fork, r["workload"])] = (r.get("repeats", "?"), int(r["tracked_bytes"]))

forks_b0 = sorted({f for (v, f) in cell if v == "B0"})
forks_v = sorted({f for (v, f) in cell if v == "V"})
common = [f for f in forks_b0 if f in forks_v]
if args.forks:
    common = common[: args.forks]
print(f"# paired forks: {len(common)} {common}")

rng = random.Random(args.seed)
workloads = sorted({w for (_, _), d in cell.items() for w in d})
print("workload,n,geomean,ci_lo,ci_hi,median_ratio,cv,oracle_ok,B0_trk,V_trk")
for w in workloads:
    ratios, oks, tb, tv = [], True, [], []
    for f in common:
        if w not in cell[("B0", f)] or w not in cell[("V", f)]:
            oks = False
            continue
        b = statistics.median(cell[("B0", f)][w])
        v = statistics.median(cell[("V", f)][w])
        ratios.append(b / v)
        o = oracle.get(("B0", f, w)) == oracle.get(("V", f, w))
        oks = oks and o
        tb.append(tracked[("B0", f, w)][1])
        tv.append(tracked[("V", f, w)][1])
    if not ratios:
        print(f"{w},0,NA,NA,NA,NA,NA,{oks},NA,NA")
        continue
    n = len(ratios)
    gm = math.exp(sum(math.log(r) for r in ratios) / n)
    boots = []
    for _ in range(args.boot):
        s = [rng.choice(ratios) for _ in range(n)]
        boots.append(math.exp(sum(math.log(r) for r in s) / n))
    boots.sort()
    lo, hi = boots[int(0.025 * args.boot)], boots[int(0.975 * args.boot)]
    med = statistics.median(ratios)
    cv = (statistics.pstdev(ratios) / (sum(ratios) / n)) if n > 1 else 0.0
    print(f"{w},{n},{gm:.4f},{lo:.4f},{hi:.4f},{med:.4f},{cv:.4f},{oks},"
          f"{statistics.median(tb):.0f},{statistics.median(tv):.0f}")
