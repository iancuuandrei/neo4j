"""Dump per-fork values for the first N cases of a paired analysis.json."""
import json
import sys

d = json.load(open(sys.argv[1]))
n = int(sys.argv[2]) if len(sys.argv) > 2 else 6
for c in d["perCase"][:n]:
    b = [round(x, 1) for x in c["baselineMs"]["values"]]
    v = [round(x, 1) for x in c["candidateMs"]["values"]]
    print(f"({c['source']},{c['target']}) d={c['distance']}")
    print(f"  B0: {b}")
    print(f"  V : {v}")
