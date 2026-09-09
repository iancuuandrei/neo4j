"""Dump per-fork oracle (rows, hash) for one workload across variants. Usage: dump_oracles.py WORKLOAD"""
import csv
import glob
import os
import sys
from collections import defaultdict

RUNS = "D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-timing-screen-v1"
w = sys.argv[1]
cell = defaultdict(dict)
for path in glob.glob(os.path.join(RUNS, "screen-*.csv")):
    for r in csv.DictReader(open(path)):
        if r["workload"] != w:
            continue
        cell[(r["variant"], r.get("fork", "?"))] = (r["rows"], r["hash"][:16], os.path.basename(path))
for key in sorted(cell):
    print(f"{key[0]:>4} fork={key[1]:>3}: rows={cell[key][0]} hash={cell[key][1]} {cell[key][2]}")
