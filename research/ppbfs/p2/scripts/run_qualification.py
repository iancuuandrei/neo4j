"""P2 paired qualification rig (research-only).

Runs P2TimingScreen in two clean worktrees (B0 vs candidate) for N fresh-JVM forks,
randomized/interleaved variant order per fork (seed = fork id). Each fork executes the full
6-workload suite (9 timed runs after 5 warmups per workload, seeded shuffle). Raw CSVs land in
PPBFS_ARTIFACTS/runs/p2-timing-screen-v1/ with variant+fork labels (append-only); surefire
reports are copied per fork. Analysis: per-fork medians -> paired ratios -> geomean + bootstrap
95% CI per workload (see analyze_qualification.py).

Usage: python run_qualification.py --forks 10 [--workload ID] [--skip-build]
"""
import argparse
import os
import random
import shutil
import subprocess
import sys
import time

B0_WORKTREE = "D:/dev/neo4j-worktrees/upstream-reference"
V_WORKTREE = "D:/dev/neo4j-worktrees/ppbfs-p2-sorted-vector"
REPO = "D:\\Caches\\Maven\\repository"
ARTIFACTS = os.environ.get("PPBFS_ARTIFACTS", "D:/dev/neo4j-research/artifacts/ppbfs")
MVN = "mvn.cmd"

BASE_ARGS = [
    f"-Dmaven.repo.local={REPO}",
    "-pl", "community/cypher/runtime-util",
    "-DsequentialTests",
    "-Dtest=P2TimingScreen",
    "-Dsurefire.failIfNoSpecifiedTests=false",
]


def run_variant(worktree, variant, fork, seed, workload):
    args = list(BASE_ARGS) + [
        f"-Dp2.variant={variant}",
        f"-Dp2.fork={fork}",
        f"-Dp2.seed={seed}",
    ]
    if workload:
        args.append(f"-Dp2.workload={workload}")
    args.append("test")
    print(f"[fork {fork}] {variant} (seed {seed}) :: {' '.join(args)}", flush=True)
    t0 = time.time()
    proc = subprocess.run([MVN] + args, cwd=worktree, capture_output=True, text=True)
    dt = time.time() - t0
    tail = "\n".join(proc.stdout.splitlines()[-8:])
    print(f"[fork {fork}] {variant} exit={proc.returncode} wall={dt:.0f}s\n{tail}", flush=True)
    # preserve surefire evidence per fork
    rep_src = os.path.join(worktree, "community/cypher/runtime-util/target/surefire-reports")
    rep_dst = os.path.join(ARTIFACTS, "runs/p2-timing-screen-v1", f"surefire-{variant}-fork{fork}")
    if os.path.isdir(rep_src):
        shutil.rmtree(rep_dst, ignore_errors=True)
        shutil.copytree(rep_src, rep_dst)
    return proc.returncode


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--forks", type=int, default=10)
    ap.add_argument("--workload", default=None)
    ap.add_argument("--start-fork", type=int, default=1)
    args = ap.parse_args()
    failures = []
    for fork in range(args.start_fork, args.start_fork + args.forks):
        order = ["B0", "V"]
        random.Random(fork).shuffle(order)
        for variant in order:
            worktree = B0_WORKTREE if variant == "B0" else V_WORKTREE
            rc = run_variant(worktree, f"{variant}", fork, fork, args.workload)
            if rc != 0:
                failures.append((fork, variant, rc))
                print(f"FAILURE fork={fork} variant={variant} rc={rc}; continuing rig", flush=True)
    print(f"done: forks={args.forks} failures={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
