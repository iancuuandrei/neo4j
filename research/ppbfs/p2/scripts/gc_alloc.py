"""GC + allocation totals from a JFR recording. Usage: python gc_alloc.py A.jfr [B.jfr ...]"""
import re
import subprocess
import sys

JFR = "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.12.101-hotspot\\bin\\jfr.exe"


def text(path, event):
    out = subprocess.run([JFR, "print", "--events", event, path],
                         capture_output=True, text=True).stdout
    return out


def gc_stats(path):
    durations, longest = [], 0.0
    for line in text(path, "jdk.GarbageCollection").splitlines():
        m = re.search(r"sumOfPauses\s*=\s*([\d,]+)\s*ms", line)
        if m:
            durations.append(float(m.group(1).replace(",", ".")))
        m2 = re.search(r"longestPause\s*=\s*([\d,]+)\s*ms", line)
        if m2:
            longest = max(longest, float(m2.group(1).replace(",", ".")))
    return len(durations), sum(durations), longest


def alloc_bytes(path):
    # No TLAB events in profile settings; use last cumulative per-thread snapshot.
    last, pending = {}, None
    for line in text(path, "jdk.ThreadAllocationStatistics").splitlines():
        m = re.search(r"allocated\s*=\s*([\d,]+)\s*(\S+)", line)
        if m:
            mult = {"bytes": 1, "KB": 1024, "MB": 1024 * 1024, "GB": 1024 * 1024 * 1024}.get(m.group(2))
            pending = float(m.group(1).replace(",", ".")) * mult if mult else None
            continue
        t = re.search(r'thread\s*=\s*"([^"]+)"', line)
        if t and pending is not None:
            last[t.group(1)] = pending
            pending = None
    import os
    if os.environ.get("P2_GC_VERBOSE"):
        for k, v in sorted(last.items(), key=lambda kv: -kv[1])[:8]:
            print(f"    {v / 1048576:.1f} MB  {k}")
    return sum(last.values()) / 1024


for path in sys.argv[1:]:
    n, total_ms, longest = gc_stats(path)
    kb = alloc_bytes(path)
    print(f"{path.split('/')[-1]}: gc_pauses={n} gc_total_ms={total_ms:.1f} "
          f"gc_max_ms={longest:.2f} thread_alloc_MB={kb/1024:.1f}")
