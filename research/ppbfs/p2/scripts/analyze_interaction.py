"""P2 4-way interaction screening: B0 / V / C1 / C1+P2 (and C4 pair) per-workload medians.
Single-JVM screening grade. Usage: python analyze_interaction.py [--runs DIR]"""
import argparse
import csv
import glob
import os
import statistics
from collections import defaultdict

ap = argparse.ArgumentParser()
ap.add_argument("--runs", default="D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-timing-screen-v1")
ap.add_argument("--variants", default="B0,V,C1,C1P2")
args = ap.parse_args()

cell = defaultdict(list)
for path in glob.glob(os.path.join(args.runs, "screen-*.csv")):
    for r in csv.DictReader(open(path)):
        # newest file per (variant, workload) wins
        cell[(r["variant"], r["workload"])].append((os.path.getmtime(path), r))

print("workload | " + " | ".join(f"{v}: med_ms / trk_KB" for v in args.variants.split(",")) + " | oracle_ok")
for w in sorted({w for (_, w) in cell}):
    parts, oracles = [], set()
    for v in args.variants.split(","):
        rows = cell.get((v, w), [])
        if not rows:
            parts.append("NA")
            continue
        mtime = max(m for m, _ in rows)
        rs = [r for m, r in rows if m == mtime]
        med = statistics.median(int(r["elapsed_ns"]) for r in rs) / 1e6
        trk = statistics.median(int(r["tracked_bytes"]) for r in rs) / 1024
        parts.append(f"{med:.1f} / {trk:.0f}")
        oracles.add((rs[0]["rows"], rs[0]["hash"]))
    print(f"{w} | " + " | ".join(parts) + f" | {len(oracles) == 1}")
