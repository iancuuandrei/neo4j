#!/usr/bin/env python3
"""Summarize allocation and GC evidence from a JFR recording."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import subprocess
from collections import defaultdict
from pathlib import Path


EVENTS = ",".join(
    (
        "jdk.ThreadAllocationStatistics",
        "jdk.ObjectAllocationInNewTLAB",
        "jdk.ObjectAllocationOutsideTLAB",
        "jdk.ObjectAllocationSample",
        "jdk.GarbageCollection",
        "jdk.GCPhasePause",
    )
)


def instant(value: str) -> dt.datetime:
    return dt.datetime.fromisoformat(value)


def duration_seconds(value: str | int | float | None) -> float:
    if value is None:
        return 0.0
    if isinstance(value, (int, float)):
        return float(value) / 1_000_000_000
    units = {"ns": 1e-9, "us": 1e-6, "ms": 1e-3, "s": 1.0}
    text = value.strip()
    if text.startswith("PT") and text.endswith("S"):
        return float(text[2:-1])
    for unit, factor in units.items():
        if text.endswith(unit):
            return float(text[: -len(unit)].strip()) * factor
    raise ValueError(f"Unsupported JFR duration: {value!r}")


def summarize(payload: dict[str, object]) -> dict[str, object]:
    events = payload["recording"]["events"]
    per_thread: dict[int, list[tuple[dt.datetime, int]]] = defaultdict(list)
    allocation_sites: dict[str, int] = defaultdict(int)
    allocation_event_count = 0
    sampled_allocation_bytes = 0
    gc_count = 0
    gc_pause_seconds = 0.0

    for event in events:
        event_type = event["type"]
        values = event["values"]
        if event_type == "jdk.ThreadAllocationStatistics":
            thread_id = int(values["thread"]["javaThreadId"])
            per_thread[thread_id].append((instant(values["startTime"]), int(values["allocated"])))
        elif event_type in {
            "jdk.ObjectAllocationInNewTLAB",
            "jdk.ObjectAllocationOutsideTLAB",
            "jdk.ObjectAllocationSample",
        }:
            allocation_event_count += 1
            amount = int(values.get("weight", values.get("tlabSize", values.get("allocationSize", 0))))
            if event_type == "jdk.ObjectAllocationSample":
                sampled_allocation_bytes += amount
            object_class = values.get("objectClass", {}).get("name", "unknown")
            frames = (values.get("stackTrace") or {}).get("frames", [])
            frame_types = [
                frame.get("method", {}).get("type", {}).get("name") for frame in frames
            ]
            top = next((name for name in frame_types if name and name.startswith("org/neo4j/")), None)
            top = top or (frame_types[0] if frame_types else None)
            allocation_sites[f"{object_class} @ {top or 'unknown'}"] += amount
        elif event_type == "jdk.GarbageCollection":
            gc_count += 1
        elif event_type == "jdk.GCPhasePause":
            gc_pause_seconds += duration_seconds(values.get("duration"))

    thread_deltas = []
    timestamps = []
    for thread_id, samples in per_thread.items():
        samples.sort()
        timestamps.extend((samples[0][0], samples[-1][0]))
        delta = max(0, samples[-1][1] - samples[0][1])
        thread_deltas.append((thread_id, delta))
    coverage_seconds = (max(timestamps) - min(timestamps)).total_seconds() if timestamps else 0.0
    allocated_bytes = sum(delta for _, delta in thread_deltas)
    top_sites = sorted(allocation_sites.items(), key=lambda item: item[1], reverse=True)[:10]
    return {
        "schemaVersion": 1,
        "threadAllocationCoverageSeconds": coverage_seconds,
        "threadAllocatedBytes": allocated_bytes,
        "threadAllocationRateBytesPerSecond": allocated_bytes / coverage_seconds if coverage_seconds else None,
        "allocationSiteEventCount": allocation_event_count,
        "sampledAllocatedBytes": sampled_allocation_bytes,
        "sampledAllocationRateBytesPerSecond": (
            sampled_allocation_bytes / coverage_seconds if coverage_seconds else None
        ),
        "topAllocationSitesByRecordedBytes": [
            {"site": site, "recordedBytes": size} for site, size in top_sites
        ],
        "gcCount": gc_count,
        "gcPauseSeconds": gc_pause_seconds,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("recording", type=Path)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    completed = subprocess.run(
        ["jfr", "print", "--json", "--events", EVENTS, str(args.recording)],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    result = summarize(json.loads(completed.stdout))
    result["recording"] = str(args.recording.resolve())
    rendered = json.dumps(result, indent=2)
    if args.output_json:
        if args.output_json.exists():
            raise FileExistsError(f"append-only protection: {args.output_json}")
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
