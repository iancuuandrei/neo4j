"""Order-stratified external analysis. Splits paired forks by observed first-variant
(from server log mtimes) and reports geomean per group + pooled. Usage:
stratify_external.py RUNS_DIR baseline_tag candidate_tag   (tags match '-<tag>-fork<N>-')
"""
import csv
import glob
import math
import os
import statistics
import sys

runs, btag, ctag = sys.argv[1], sys.argv[2], sys.argv[3]

# fork -> first variant
order = {}
forks = set()
for path in glob.glob(os.path.join(runs, "*.out.log")):
    base = os.path.basename(path)
    fork = base.split("-fork")[1].split("-")[0]
    forks.add(fork)
for fork in sorted(forks):
    times = {}
    for variant, tag in (("base", btag), ("cand", ctag)):
        files = glob.glob(os.path.join(runs, f"*-{tag}-fork{fork}-*.out.log"))
        if files:
            times[variant] = os.path.getmtime(files[0])
    if times:
        order[fork] = min(times, key=times.get)

# per-pair per-fork medians
b0, v = {}, {}
for path in glob.glob(os.path.join(runs, "*.csv")):
    base = os.path.basename(path)
    fork = base.split("-fork")[1].split("-")[0]
    variant = "v" if f"-{ctag}fork" in base or base.split("-fork")[0].endswith(ctag) else "b0"
    # robust: candidate files contain '-candidate-fork', baseline '-b0-fork'
    if "-candidate-fork" in base:
        variant = "v"
    elif "-b0-fork" in base:
        variant = "b0"
    else:
        continue
    for r in csv.DictReader(open(path)):
        (v if variant == "v" else b0).setdefault((r["source"], r["target"]), {}).setdefault(fork, []).append(
            int(r["elapsed_ns"]))

print(f"fork order: { {f: order.get(f) for f in sorted(forks)} }")
for group in ("base", "cand", "pooled"):
    ratios = []
    for key in sorted(set(b0) & set(v)):
        for f in sorted(set(b0[key]) & set(v[key])):
            if group != "pooled" and order.get(f) != group:
                continue
            ratios.append(statistics.median(b0[key][f]) / statistics.median(v[key][f]))
    if ratios:
        gm = math.exp(sum(math.log(r) for r in ratios) / len(ratios))
        print(f"{group}: n={len(ratios)} geomean={gm:.4f} min={min(ratios):.3f} max={max(ratios):.3f}")
    else:
        print(f"{group}: no data")
