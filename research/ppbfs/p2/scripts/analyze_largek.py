"""P2 large-k danger-zone analysis. Usage: python analyze_largek.py [CSV]"""
import csv
import glob
import os
import sys

RUNS = "D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-largek-v1"
path = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.join(RUNS, "largek-*.csv")))[-1]
rows = list(csv.DictReader(open(path)))
print(f"# cells: {len(rows)} from {os.path.basename(path)}")
print("S,k,pattern,op,D_ns,V_ns,V/D")
for r in rows:
    d, v = float(r["D_ns"]), float(r["V_ns"])
    print(f"{r['S']},{r['k']},{r['pattern']},{r['op']},{d:.1f},{v:.1f},{v/d:.2f}")
