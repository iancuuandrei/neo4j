#!/usr/bin/env python3
"""Validate and summarize metadata-bound paired PPBFS benchmark forks."""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


T_975 = {
    1: 12.706,
    2: 4.303,
    3: 3.182,
    4: 2.776,
    5: 2.571,
    6: 2.447,
    7: 2.365,
    8: 2.306,
    9: 2.262,
    10: 2.228,
    11: 2.201,
    12: 2.179,
    13: 2.160,
    14: 2.145,
    15: 2.131,
    16: 2.120,
    17: 2.110,
    18: 2.101,
    19: 2.093,
    20: 2.086,
    21: 2.080,
    22: 2.074,
    23: 2.069,
    24: 2.064,
    25: 2.060,
    26: 2.056,
    27: 2.052,
    28: 2.048,
    29: 2.045,
    30: 2.042,
}


def percentile(values: list[float], probability: float) -> float:
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * probability
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    fraction = position - lower
    return ordered[lower] * (1 - fraction) + ordered[upper] * fraction


def geometric_mean(values: Iterable[float]) -> float:
    logged = [math.log(value) for value in values]
    return math.exp(statistics.fmean(logged))


def summarize_speedups(speedups: list[float]) -> dict[str, float | int]:
    logs = [math.log(value) for value in speedups]
    mean_log = statistics.fmean(logs)
    if len(logs) > 1:
        standard_deviation = statistics.stdev(logs)
        critical = T_975.get(len(logs) - 1, 1.96)
        margin = critical * standard_deviation / math.sqrt(len(logs))
        lower = math.exp(mean_log - margin)
        upper = math.exp(mean_log + margin)
        effect_size = mean_log / standard_deviation if standard_deviation else math.inf
    else:
        lower = upper = math.exp(mean_log)
        effect_size = math.nan
    return {
        "forks": len(speedups),
        "geometricMeanSpeedup": math.exp(mean_log),
        "medianSpeedup": statistics.median(speedups),
        "confidence95Lower": lower,
        "confidence95Upper": upper,
        "pairedLogCohenDz": effect_size,
        "minimumSpeedup": min(speedups),
        "maximumSpeedup": max(speedups),
    }


def load_csv(path: Path) -> dict[int, dict[str, Any]]:
    by_distance: dict[int, dict[str, Any]] = {}
    seen: set[tuple[int, int]] = set()
    with path.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise ValueError(f"No measurements in {path}")
    for row in rows:
        distance = int(row["distance"])
        repetition = int(row["repetition"])
        key = (distance, repetition)
        if key in seen:
            raise ValueError(f"Duplicate distance/repetition in {path}: {key}")
        seen.add(key)
        lengths = tuple(int(value) for value in row["result_lengths"].split(";"))
        entry = by_distance.setdefault(distance, {"elapsedNs": [], "resultLengths": lengths})
        if entry["resultLengths"] != lengths:
            raise ValueError(f"Result drift within {path} at distance {distance}")
        entry["elapsedNs"].append(int(row["elapsed_ns"]))

    repetition_counts = {len(entry["elapsedNs"]) for entry in by_distance.values()}
    if len(repetition_counts) != 1:
        raise ValueError(f"Repetition-count drift in {path}: {sorted(repetition_counts)}")
    return by_distance


def discover_runs(
    runs_dir: Path, baseline_sha: str, candidate_sha: str
) -> tuple[dict[int, dict[int, dict[str, Any]]], dict[int, dict[int, dict[str, Any]]], dict[str, Any]]:
    variants: dict[str, dict[int, dict[int, dict[str, Any]]]] = {
        baseline_sha: {},
        candidate_sha: {},
    }
    common_hashes: dict[str, set[str]] = defaultdict(set)
    runtime_hashes: dict[str, set[str]] = defaultdict(set)
    protocol_values: dict[str, set[int]] = defaultdict(set)

    for metadata_path in sorted(runs_dir.glob("*.metadata.json")):
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        variant_sha = metadata["variantSha"]
        if variant_sha not in variants:
            continue
        csv_path = metadata_path.with_name(metadata_path.name.removesuffix(".metadata.json") + ".csv")
        if not csv_path.exists() or csv_path.stat().st_size == 0:
            raise ValueError(f"Missing non-empty CSV for {metadata_path.name}: {csv_path.name}")
        seed = int(metadata["protocol"]["seed"])
        if seed in variants[variant_sha]:
            raise ValueError(f"Duplicate metadata-bound seed {seed} for variant {variant_sha}")
        variants[variant_sha][seed] = load_csv(csv_path)
        for key in ("dataset", "queryManifest", "config"):
            common_hashes[key].add(metadata["inputs"][key]["sha256"])
        runtime_hashes[variant_sha].add(metadata["inputs"]["distribution"]["runtimeJar"]["sha256"])
        for key in ("warmups", "repetitions"):
            protocol_values[key].add(int(metadata["protocol"][key]))

    for name, hashes in common_hashes.items():
        if len(hashes) != 1:
            raise ValueError(f"Input hash drift for {name}: {sorted(hashes)}")
    for variant_sha, hashes in runtime_hashes.items():
        if len(hashes) != 1:
            raise ValueError(f"Runtime JAR drift for {variant_sha}: {sorted(hashes)}")
    for name, values in protocol_values.items():
        if len(values) != 1:
            raise ValueError(f"Protocol drift for {name}: {sorted(values)}")

    baseline_seeds = set(variants[baseline_sha])
    candidate_seeds = set(variants[candidate_sha])
    if baseline_seeds != candidate_seeds:
        raise ValueError(
            f"Unpaired seeds: baseline-only={sorted(baseline_seeds - candidate_seeds)}, "
            f"candidate-only={sorted(candidate_seeds - baseline_seeds)}"
        )
    if len(baseline_seeds) < 2:
        raise ValueError("At least two paired JVM forks are required")

    binding = {
        "seeds": sorted(baseline_seeds),
        "inputHashes": {name: next(iter(hashes)) for name, hashes in common_hashes.items()},
        "runtimeJarHashes": {sha: next(iter(hashes)) for sha, hashes in runtime_hashes.items()},
        "protocol": {name: next(iter(values)) for name, values in protocol_values.items()},
    }
    return variants[baseline_sha], variants[candidate_sha], binding


def analyze(
    baseline: dict[int, dict[int, dict[str, Any]]],
    candidate: dict[int, dict[int, dict[str, Any]]],
) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    seeds = sorted(baseline)
    distances = sorted(baseline[seeds[0]])
    per_distance: list[dict[str, Any]] = []

    for seed in seeds:
        if sorted(baseline[seed]) != distances or sorted(candidate[seed]) != distances:
            raise ValueError(f"Distance-set drift in seed {seed}")

    for distance in distances:
        baseline_ms: list[float] = []
        candidate_ms: list[float] = []
        speedups: list[float] = []
        for seed in seeds:
            before = baseline[seed][distance]
            after = candidate[seed][distance]
            if before["resultLengths"] != after["resultLengths"]:
                raise ValueError(
                    f"Result mismatch seed={seed} distance={distance}: "
                    f"{before['resultLengths']} vs {after['resultLengths']}"
                )
            before_median = statistics.median(before["elapsedNs"])
            after_median = statistics.median(after["elapsedNs"])
            baseline_ms.append(before_median / 1_000_000)
            candidate_ms.append(after_median / 1_000_000)
            speedups.append(before_median / after_median)
        per_distance.append(
            {
                "distance": distance,
                "resultLengths": list(baseline[seeds[0]][distance]["resultLengths"]),
                "baselineMs": {
                    "median": statistics.median(baseline_ms),
                    "p95": percentile(baseline_ms, 0.95),
                    "p99": percentile(baseline_ms, 0.99),
                    "minimum": min(baseline_ms),
                    "maximum": max(baseline_ms),
                    "values": baseline_ms,
                },
                "candidateMs": {
                    "median": statistics.median(candidate_ms),
                    "p95": percentile(candidate_ms, 0.95),
                    "p99": percentile(candidate_ms, 0.99),
                    "minimum": min(candidate_ms),
                    "maximum": max(candidate_ms),
                    "values": candidate_ms,
                },
                "pairedSpeedup": summarize_speedups(speedups),
                "speedupsBySeed": dict(zip(map(str, seeds), speedups, strict=True)),
            }
        )

    groups = {
        "all": distances,
        "shallow_10_100": [distance for distance in distances if distance <= 100],
        "deep_250_772": [distance for distance in distances if distance >= 250],
    }
    aggregates: dict[str, dict[str, Any]] = {}
    for name, selected in groups.items():
        per_seed = []
        for seed in seeds:
            per_seed.append(
                geometric_mean(
                    statistics.median(baseline[seed][distance]["elapsedNs"])
                    / statistics.median(candidate[seed][distance]["elapsedNs"])
                    for distance in selected
                )
            )
        aggregates[name] = {
            "distances": selected,
            "pairedSpeedup": summarize_speedups(per_seed),
            "speedupsBySeed": dict(zip(map(str, seeds), per_seed, strict=True)),
        }
    return per_distance, aggregates


def markdown_report(result: dict[str, Any]) -> str:
    baseline_label = result["baselineLabel"]
    candidate_label = result["candidateLabel"]
    lines = [
        f"# {result['title']}",
        "",
        "**MEASURED:** Warm-cache HTTP end-to-end timings from metadata-bound JVM forks.",
        f"Speedup is {baseline_label} elapsed time divided by {candidate_label} elapsed time.",
        "Confidence intervals are two-sided 95% Student-t intervals over paired",
        "log speedups of per-fork medians.",
        "",
        f"Paired seeds: `{', '.join(map(str, result['binding']['seeds']))}`",
        "",
        f"| Distance | {baseline_label} median ms | {candidate_label} median ms | Geomean speedup | 95% CI | Min–max |",
        "| ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in result["perDistance"]:
        speedup = row["pairedSpeedup"]
        lines.append(
            f"| {row['distance']} | {row['baselineMs']['median']:.3f} | "
            f"{row['candidateMs']['median']:.3f} | {speedup['geometricMeanSpeedup']:.3f}× | "
            f"[{speedup['confidence95Lower']:.3f}, {speedup['confidence95Upper']:.3f}] | "
            f"{speedup['minimumSpeedup']:.3f}–{speedup['maximumSpeedup']:.3f}× |"
        )
    lines.extend(["", "## Aggregates", ""])
    for name, aggregate in result["aggregates"].items():
        speedup = aggregate["pairedSpeedup"]
        lines.append(
            f"- `{name}` distances {aggregate['distances']}: "
            f"{speedup['geometricMeanSpeedup']:.3f}× "
            f"(95% CI {speedup['confidence95Lower']:.3f}–{speedup['confidence95Upper']:.3f}×)."
        )
    lines.extend(
        [
            "",
            "All paired result-length sets matched. This analysis is one real graph and",
            "does not by itself satisfy the full multi-topology benefit, memory, regression,",
            "or correctness gates.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("runs_dir", type=Path)
    parser.add_argument("--baseline-sha", required=True)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--title", default="Paired PPBFS timing analysis")
    parser.add_argument("--baseline-label", default="baseline")
    parser.add_argument("--candidate-label", default="candidate")
    parser.add_argument("--output-json", type=Path, required=True)
    parser.add_argument("--output-markdown", type=Path, required=True)
    args = parser.parse_args()

    baseline, candidate, binding = discover_runs(
        args.runs_dir, args.baseline_sha, args.candidate_sha
    )
    per_distance, aggregates = analyze(baseline, candidate)
    result = {
        "schemaVersion": 1,
        "evidenceLabel": "MEASURED",
        "method": "paired log speedup with two-sided 95% Student-t confidence interval",
        "baselineVariantSha": args.baseline_sha,
        "candidateVariantSha": args.candidate_sha,
        "title": args.title,
        "baselineLabel": args.baseline_label,
        "candidateLabel": args.candidate_label,
        "binding": binding,
        "perDistance": per_distance,
        "aggregates": aggregates,
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_markdown.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    args.output_markdown.write_text(markdown_report(result), encoding="utf-8")
    print(markdown_report(result))


if __name__ == "__main__":
    main()
