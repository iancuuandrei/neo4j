"""P2 JFR attribution (research-only). Aggregates ExecutionSample top frames and
ObjectAllocationSample classes from a profile JFR. Usage: python analyze_jfr.py FILE.jfr"""
import re
import subprocess
import sys
from collections import Counter

JFR = "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.12.101-hotspot\\bin\\jfr.exe"

INTEREST = ("BFSExpander", "FoundNodes", "StateBucket", "HeapTrackingArrayList",
            "HeapTrackingLongObjectHashMap", "ProductGraphTraversalCursor", "NodeState",
            "PGPathPropagatingBFS", "Propagator", "PathTracer", "TwoWaySignpost", "Lengths")

def samples(path, event):
    out = subprocess.run([JFR, "print", "--events", event, "--stack-depth", "64", path],
                         capture_output=True, text=True).stdout
    blocks = out.split(event + " {")
    stacks = []
    for b in blocks[1:]:
        frames = []
        in_stack = False
        for line in b.splitlines():
            if "stackTrace = [" in line:
                in_stack = True
                continue
            if in_stack:
                if "]" in line:
                    break
                m = re.search(r"^\s*([a-zA-Z0-9_.$]+)\.([a-zA-Z0-9_$<>]+)\(.*\)", line)
                if m:
                    frames.append(m.group(1) + "." + m.group(2))
        if frames:
            stacks.append(frames)
    return stacks

def main():
    path = sys.argv[1]
    stacks = samples(path, "jdk.ExecutionSample")
    n = len(stacks)
    print(f"# execution samples: {n}")
    leaf = Counter(s[0] for s in stacks)
    print("--- leaf methods (top 30) ---")
    for method, count in leaf.most_common(30):
        print(f"{count:6d} {100.0*count/n:5.1f}% {method}")
    print("--- inclusive: % of samples whose stack contains class ---")
    for key in INTEREST:
        c = sum(1 for s in stacks if any(key in f for f in s))
        print(f"{c:6d} {100.0*c/max(n,1):5.1f}% {key}")
    print("=== allocations (top classes) ===")
    astacks = samples(path, "jdk.ObjectAllocationSample")
    print(f"# allocation samples: {len(astacks)}")
    allocs = Counter()
    for s in astacks:
        # allocation samples carry the class in event fields, not the stack; re-scan raw text
        pass
    out = subprocess.run([JFR, "print", "--events", "jdk.ObjectAllocationSample", path],
                         capture_output=True, text=True).stdout
    for line in out.splitlines():
        m2 = re.search(r"objectClass\s*=\s*(.+?)\s*\(classLoader", line)
        if m2:
            allocs[m2.group(1).strip()] += 1
    for cls, count in allocs.most_common(25):
        print(f"{count:6d} {cls}")

if __name__ == "__main__":
    main()
