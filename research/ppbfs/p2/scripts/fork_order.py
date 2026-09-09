"""Determine per-fork variant execution order from server log timestamps."""
import glob
import os

runs = "D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-external-v1/hetionet-H3-b0-vs-p2v2"
for fork in (1, 2, 3, 4, 5):
    times = {}
    for variant in ("b0", "candidate"):
        pat = os.path.join(runs, f"*-{variant}-fork{fork}-*.out.log")
        files = glob.glob(pat)
        if files:
            times[variant] = os.path.getmtime(files[0])
    order = sorted(times, key=times.get)
    detail = {k: round(v, 1) for k, v in times.items()}
    print(f"fork{fork}: order={order} mtime={detail}")
