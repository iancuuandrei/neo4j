"""P2 telemetry aggregation (research-only analysis, runs on D: artifacts, prints Markdown).

Usage: python analyze_telemetry.py [--runs DIR] [--out MANIFEST.csv]
Selects the latest timestamped run dir per workload id, aggregates per-bucket
CSVs, and prints summary + distribution tables for SPARSITY_REPORT.md.
Raw data is never modified. New suite executions create new timestamped dirs;
rerunning this script only re-reads.
"""
import csv
import sys
from collections import defaultdict
from pathlib import Path

RUNS = Path(sys.argv[sys.argv.index("--runs") + 1]) if "--runs" in sys.argv else Path(
    "D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-telemetry-v1")

# Superseded workload definitions (append-only artifacts retained on disk, excluded from analysis).
EXCLUDED = {"grid-s63"}

def latest_runs():
    best = {}
    for d in RUNS.iterdir():
        if not d.is_dir():
            continue
        wid = d.name.rsplit("-", 1)[0] if "-" in d.name else d.name
        # workload ids contain no trailing timestamp; split off the ISO stamp at first digit-run of the stamp
        # Safer: workload id is the name minus the last -<stamp> suffix (stamp starts with 2026-).
        idx = d.name.find("-2026-")
        wid = d.name[:idx] if idx > 0 else d.name
        if wid not in best or d.name > best[wid].name:
            best[wid] = d
    return best

def read_csv(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))

def quant(xs, q):
    if not xs:
        return float("nan")
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(q * len(xs)))]

def main():
    runs = latest_runs()
    runs = {k: v for k, v in runs.items() if k not in EXCLUDED}
    print(f"# workloads: {len(runs)} (excluded superseded: {sorted(EXCLUDED)})")
    hdr = ["workload", "S", "buckets", "rows", "meanOcc", "p50Occ", "p95Occ",
           "nullFrac", "slots", "active", "g_front", "iters", "g/i",
           "canonHit", "canonMiss", "hitBuf", "hitFwd", "hitBwd", "hitHist",
           "missBuf", "missFwd", "missBwd", "missHist",
           "nodeMissBuf", "nodeMissFwd", "nodeMissBwd", "nodeMissHist",
           "histProbes", "writes", "dups",
           "mono%", "adjWr", "meanSpan", "meanRuns", "meanOcc8", "maxK"]
    rows_out = []
    for wid in sorted(runs):
        d = runs[wid]
        summ = {r["metric"]: r["value"] for r in read_csv(d / "summary.csv")}
        oracle = {}
        for line in (d / "oracle.txt").read_text().splitlines():
            k, _, v = line.partition("=")
            oracle[k.strip()] = v.strip()
        buckets = read_csv(d / "buckets.csv")
        S = int(summ["S"])
        occ = [int(b["kFinal"]) / S for b in buckets]
        spans = [int(b["span"]) for b in buckets if int(b["kFinal"]) > 0]
        runs_n = [int(b["runs"]) for b in buckets if int(b["kFinal"]) > 0]
        occ8 = [int(b["occ8"]) for b in buckets]
        maxk = max([int(b["kFinal"]) for b in buckets] or [0])
        g = int(summ["frontierHits"]) + int(summ["frontierMisses"])
        iters = int(summ["iterations"])
        n = int(summ["buckets"])
        mono = 100.0 * sum(1 for b in buckets if b["monotonic"] == "1") / max(n, 1)
        rows_out.append([wid, S, n, oracle.get("rows"), f"{sum(occ)/max(len(occ),1):.4f}",
                         f"{quant(occ,0.5):.4f}", f"{quant(occ,0.95):.4f}",
                         summ["nullFractionOfScans"], summ["slotsScanned"], summ["activeEmitted"],
                         g, iters, f"{(g/max(iters,1)):.1f}",
                         summ["canonHits"], summ["canonMisses"],
                         summ.get("canonHitBuffer", 0), summ.get("canonHitForward", 0),
                         summ.get("canonHitBackward", 0), summ.get("canonHitHistory", 0),
                         summ.get("canonMissBuffer", 0), summ.get("canonMissForward", 0),
                         summ.get("canonMissBackward", 0), summ.get("canonMissHistory", 0),
                         summ.get("nodeMissBuffer", 0), summ.get("nodeMissForward", 0),
                         summ.get("nodeMissBackward", 0), summ.get("nodeMissHistory", 0),
                         summ["historyLevelProbes"],
                         summ["writes"], summ["duplicateWrites"], f"{mono:.0f}",
                         summ["adjacentWrites"],
                         f"{(sum(spans)/max(len(spans),1)):.1f}",
                         f"{(sum(runs_n)/max(len(runs_n),1)):.2f}",
                         f"{(sum(occ8)/max(len(occ8),1)):.2f}", maxk])
    print(",".join(hdr))
    for r in rows_out:
        print(",".join(str(x) for x in r))
    if "--out" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--out") + 1])
        with open(out, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(hdr)
            w.writerows(rows_out)
        print(f"# manifest written to {out}", file=sys.stderr)

if __name__ == "__main__":
    main()
