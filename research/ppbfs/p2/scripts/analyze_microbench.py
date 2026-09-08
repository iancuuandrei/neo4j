"""P2 microbench screening analysis. Prints ratio pivots per op; used for MICROBENCH_RESULTS.md."""
import csv
import glob
import os
import sys

RUNS = "D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-microbench-v1"
path = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.join(RUNS, "screening-*.csv")))[-1]
rows = list(csv.DictReader(open(path)))
print(f"# cells: {len(rows)} from {os.path.basename(path)}")
for op in ("hit", "miss", "iter", "construct", "insert", "merge"):
    print(f"--- {op} (pattern=low): S,k : V/D DA/D H/D  [ns D,V,DA,H] ---")
    for r in rows:
        if r["op"] == op and r["pattern"] == "low":
            d, v, da, h = (float(r[c]) for c in ("D_ns", "V_ns", "DA_ns", "H_ns"))
            print(f"S={r['S']:>3} k={r['k']:>2}: {v/d:6.2f} {da/d:6.2f} {h/d:6.2f}  "
                  f"[{d:7.1f} {v:7.1f} {da:7.1f} {h:7.1f}]")
print("=== pattern sensitivity: V/D hit+iter for high/dispersed (S=128) ===")
for r in rows:
    if r["S"] == "128" and r["op"] in ("hit", "iter") and r["pattern"] != "low":
        d, v = float(r["D_ns"]), float(r["V_ns"])
        print(f"{r['op']:>4} {r['pattern']:>9} k={r['k']:>2}: V/D={v/d:.2f} [{d:.1f} vs {v:.1f}]")
