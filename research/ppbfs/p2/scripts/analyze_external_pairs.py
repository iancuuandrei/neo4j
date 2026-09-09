"""Per-pair P2 external analysis. For each (source,target): paired ratios across forks.
Usage: python analyze_external_pairs.py RUNS_DIR"""
import csv
import glob
import math
import os
import statistics
import sys

runs = sys.argv[1]
b0, v = {}, {}
for path in glob.glob(os.path.join(runs, "*.csv")):
    base = os.path.basename(path)
    variant = "b0" if "-b0-fork" in base else "v"
    fork = base.split("-fork")[1].split("-")[0]
    for r in csv.DictReader(open(path)):
        key = (r["source"], r["target"])
        (b0 if variant == "b0" else v).setdefault(key, {}).setdefault(fork, []).append(int(r["elapsed_ns"]))

print("source,target,n,geom_ratio(B0/V),min,max,median_B0ms,median_Vms")
for key in sorted(set(b0) & set(v)):
    ratios = []
    for f in sorted(set(b0[key]) & set(v[key])):
        ratios.append(statistics.median(b0[key][f]) / statistics.median(v[key][f]))
    if not ratios:
        continue
    gm = math.exp(sum(math.log(r) for r in ratios) / len(ratios))
    bmed = statistics.median([statistics.median(b0[key][f]) for f in b0[key]]) / 1e6
    vmed = statistics.median([statistics.median(v[key][f]) for f in v[key]]) / 1e6
    print(f"{key[0]},{key[1]},{len(ratios)},{gm:.3f},{min(ratios):.3f},{max(ratios):.3f},{bmed:.1f},{vmed:.1f}")
