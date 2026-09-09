"""P2 final-qualification rig (research-only, orchestration branch).

Paired fresh-JVM forks for ANY two variants in ANY two worktrees over a workload subset:
randomized variant order per fork (seed = fork id), identical harness file everywhere,
5 warmups + 9 timed runs per workload, per-fork medians. Raw CSVs append to
PPBFS_ARTIFACTS/runs/p2-timing-screen-v1/ with variant+fork labels (append-only); surefire
reports copied per fork. Analyze with analyze_qualification.py (pairs by fork label).

Usage:
  python run_final_qual.py --a B0 --b V --wa D:/dev/neo4j-worktrees/upstream-reference \\
      --wb D:/dev/neo4j-worktrees/ppbfs-p2-sorted-vector \\
      --workloads chain2000-s255,k32-s34 --forks 10 [--start-fork 1]
"""
import argparse
import os
import random
import shutil
import subprocess
import sys
import time

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


def run_variant(worktree, variant, fork, seed, workloads):
    args = list(BASE_ARGS) + [
        f"-Dp2.variant={variant}",
        f"-Dp2.fork={fork}",
        f"-Dp2.seed={seed}",
        f"-Dp2.workloads={','.join(workloads)}",
        "test",
    ]
    print(f"[fork {fork}] {variant} workloads={len(workloads)} (seed {seed})", flush=True)
    t0 = time.time()
    proc = subprocess.run([MVN] + args, cwd=worktree, capture_output=True, text=True)
    dt = time.time() - t0
    tail = "\n".join(proc.stdout.splitlines()[-6:])
    print(f"[fork {fork}] {variant} exit={proc.returncode} wall={dt:.0f}s\n{tail}", flush=True)
    rep_src = os.path.join(worktree, "community/cypher/runtime-util/target/surefire-reports")
    rep_dst = os.path.join(ARTIFACTS, "runs/p2-timing-screen-v1", f"surefire-{variant}-fork{fork}")
    if os.path.isdir(rep_src):
        shutil.rmtree(rep_dst, ignore_errors=True)
        shutil.copytree(rep_src, rep_dst)
    return proc.returncode


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", required=True)
    ap.add_argument("--b", required=True)
    ap.add_argument("--wa", required=True)
    ap.add_argument("--wb", required=True)
    ap.add_argument("--workloads", required=True)
    ap.add_argument("--forks", type=int, default=10)
    ap.add_argument("--start-fork", type=int, default=1)
    args = ap.parse_args()
    workloads = args.workloads.split(",")
    variants = {"A": (args.a, args.wa), "B": (args.b, args.wb)}
    failures = []
    for fork in range(args.start_fork, args.start_fork + args.forks):
        order = ["A", "B"]
        random.Random(1000 + fork).shuffle(order)
        for side in order:
            variant, worktree = variants[side]
            rc = run_variant(worktree, variant, fork, 1000 + fork, workloads)
            if rc != 0:
                failures.append((fork, variant, rc))
                print(f"FAILURE fork={fork} variant={variant} rc={rc}; continuing rig", flush=True)
    print(f"done: forks={args.forks} failures={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
