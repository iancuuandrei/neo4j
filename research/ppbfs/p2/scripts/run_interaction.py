"""P2 interaction matrix driver (research-only). Runs P2TimingScreen per workload for
B0 / V / C1 / C1+P2 / C4 / C4+P2 worktrees (single JVM each; screening grade).
Usage: python run_interaction.py [--variants B0,V,C1,C1P2]
"""
import argparse
import subprocess
import sys

WORKTREES = {
    "B0": "D:/dev/neo4j-worktrees/upstream-reference",
    "V": "D:/dev/neo4j-worktrees/ppbfs-p2-sorted-vector",
    "C1": "D:/dev/neo4j-worktrees/ppbfs-direct-state-index",
    "C1P2": "D:/dev/neo4j-worktrees/ppbfs-p2-c1-interaction",
    "C4": "D:/dev/neo4j-worktrees/ppbfs-deferred-state-index",
    "C4P2": "D:/dev/neo4j-worktrees/ppbfs-p2-c4-interaction",
}
WORKLOADS = [
    "chain2000-s255", "chain2000-s509", "chain2000-s31", "star2000-s33",
    "grid30-s19", "h3analog-s5", "h3analog-bidi2-s5",
    "diamond-dense-s18", "tiny-chain500-s4",
]
REPO = "D:\\Caches\\Maven\\repository"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--variants", default="B0,V,C1,C1P2")
    ap.add_argument("--workloads", default=",".join(WORKLOADS))
    args = ap.parse_args()
    failures = []
    for variant in args.variants.split(","):
        for workload in args.workloads.split(","):
            cmd = ["mvn.cmd", f"-Dmaven.repo.local={REPO}", "-pl", "community/cypher/runtime-util",
                   "-DsequentialTests", "-Dtest=P2TimingScreen",
                   "-Dsurefire.failIfNoSpecifiedTests=false",
                   f"-Dp2.variant={variant}", f"-Dp2.workload={workload}", "test"]
            print(f"### {variant}/{workload}", flush=True)
            try:
                proc = subprocess.run(cmd, cwd=WORKTREES[variant], capture_output=True, text=True, timeout=1200)
            except subprocess.TimeoutExpired:
                print(f"HANG/TIMEOUT {variant}/{workload}", flush=True)
                failures.append((variant, workload, "timeout"))
                subprocess.run(["taskkill", "/F", "/IM", "java.exe"], capture_output=True)
                continue
            tail = [l for l in proc.stdout.splitlines() if "median_ns" in l or "Tests run:" in l]
            print("\n".join(tail[-3:]), flush=True)
            if proc.returncode != 0:
                failures.append((variant, workload, proc.returncode))
    print(f"failures={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
