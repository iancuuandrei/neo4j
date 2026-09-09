"""Per-pair P2 external analysis. For each (source,target): paired ratios across forks.
Usage: python analyze_external_pairs.py RUNS_DIR [RUNS_DIR2 ...] (pooled across dirs)"""
import csv
import glob
import math
import os
import statistics
import sys

runs = sys.argv[1:]
b0, v = {}, {}
for run in runs:
    tag = os.path.basename(run.rstrip("/\\"))
    for path in glob.glob(os.path.join(run, "*.csv")):
        base = os.path.basename(path)
        variant = "b0" if "-b0-fork" in base else "v"
        fork = f"{tag}#{base.split('-fork')[1].split('-')[0]}"
        for r in csv.DictReader(open(path)):
            key = (r["source"], r["target"])
            (b0 if variant == "b0" else v).setdefault(key, {}).setdefault(fork, []).append(int(r["elapsed_ns"]))

print("source,target,n,geom_ratio(B0/V),min,max,median_B0ms,median_Vms")
all_ratios = []
for key in sorted(set(b0) & set(v)):
    ratios = []
    for f in sorted(set(b0[key]) & set(v[key])):
        ratios.append(statistics.median(b0[key][f]) / statistics.median(v[key][f]))
    if not ratios:
        continue
    all_ratios.extend(ratios)
    gm = math.exp(sum(math.log(r) for r in ratios) / len(ratios))
    bmed = statistics.median([statistics.median(b0[key][f]) for f in b0[key]]) / 1e6
    vmed = statistics.median([statistics.median(v[key][f]) for f in v[key]]) / 1e6
    print(f"{key[0]},{key[1]},{len(ratios)},{gm:.3f},{min(ratios):.3f},{max(ratios):.3f},{bmed:.1f},{vmed:.1f}")
if all_ratios:
    gm = math.exp(sum(math.log(r) for r in all_ratios) / len(all_ratios))
    print(f"POOLED,n={len(all_ratios)},geomean={gm:.4f},min={min(all_ratios):.3f},max={max(all_ratios):.3f}")
