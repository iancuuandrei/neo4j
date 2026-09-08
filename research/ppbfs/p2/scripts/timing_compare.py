"""P2 timing-screen comparison. Compares latest B0 vs V screen CSVs: oracle equality,
median ratios, tracked-bytes deltas. Screening grade (single JVM per variant)."""
import csv
import glob
import os
import statistics
from collections import defaultdict

RUNS = "D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-timing-screen-v1"

def latest(variant):
    cands = sorted(glob.glob(os.path.join(RUNS, f"screen-{variant}-*.csv")))
    # prefer multi-fork qualification files when present
    quals = [c for c in cands if "-fork" in os.path.basename(c)]
    pool = quals if quals else cands
    return pool[-1]

def load(path):
    data = defaultdict(list)
    for r in csv.DictReader(open(path)):
        data[r["workload"]].append(r)
    return data

def med(xs):
    return statistics.median(xs)

b0 = load(latest("B0"))
v = load(latest("V"))
print(f"B0={os.path.basename(latest('B0'))}")
print(f"V ={os.path.basename(latest('V'))}")
print("workload,S,rows,B0_med_ns,V_med_ns,ratio(V faster>1),B0_trk,V_trk,trk_ratio,oracle_match")
for w in sorted(set(b0) & set(v)):
    b, c = b0[w], v[w]
    b_rows = {r["rows"] for r in b} | {r["rows"] for r in c}
    b_hash = {r["hash"] for r in b} | {r["hash"] for r in c}
    ok = len(b_rows) == 1 and len(b_hash) == 1
    bm, vm = med([int(r["elapsed_ns"]) for r in b]), med([int(r["elapsed_ns"]) for r in c])
    bt, vt = med([int(r["tracked_bytes"]) for r in b]), med([int(r["tracked_bytes"]) for r in c])
    print(f"{w},{b[0]['S']},{b_rows},B0={bm:.0f},V={vm:.0f},{bm/vm:.3f},"
          f"{bt:.0f},{vt:.0f},{bt/vt:.3f},{ok}")
