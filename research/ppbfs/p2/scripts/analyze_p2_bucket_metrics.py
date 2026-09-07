#!/usr/bin/env python3
"""Validate and summarize PPBFS P2 concrete state-bucket telemetry.

The input is append-only JSONL emitted by P2StateBucketTelemetry. This script
uses only the Python standard library so the analysis can run in a clean
research checkout without adding project dependencies.
"""

from __future__ import annotations

import argparse
import csv
import glob
import json
import math
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

SCHEMA_VERSION = 1
CAUSAL_BASELINE_SHA = "f213380f812b820a1b312e2ea52cb3d8f1931ccc"
LOOKUP_ROLES = (
    "BUFFER",
    "FORWARD_FRONTIER",
    "BACKWARD_FRONTIER",
    "HISTORY",
    "EXPANSION",
)


class EvidenceError(ValueError):
    """Raised when a telemetry row violates the frozen evidence contract."""


def _as_int_hist(value: Any, field: str) -> dict[int, int]:
    if not isinstance(value, dict):
        raise EvidenceError(f"{field} must be a JSON object")
    out: dict[int, int] = {}
    for raw_key, raw_count in value.items():
        try:
            key = int(raw_key)
        except (TypeError, ValueError) as exc:
            raise EvidenceError(f"{field} has non-integer key {raw_key!r}") from exc
        if not isinstance(raw_count, int) or isinstance(raw_count, bool) or raw_count < 0:
            raise EvidenceError(f"{field}[{raw_key!r}] must be a non-negative integer")
        if key < 0:
            raise EvidenceError(f"{field} has negative key {key}")
        if raw_count:
            out[key] = raw_count
    return out


def _as_role_counts(value: Any, field: str) -> dict[str, int]:
    if not isinstance(value, dict):
        raise EvidenceError(f"{field} must be a JSON object")
    unknown = set(value) - set(LOOKUP_ROLES)
    missing = set(LOOKUP_ROLES) - set(value)
    if unknown or missing:
        raise EvidenceError(f"{field} role mismatch: missing={sorted(missing)}, unknown={sorted(unknown)}")
    out: dict[str, int] = {}
    for role in LOOKUP_ROLES:
        count = value[role]
        if not isinstance(count, int) or isinstance(count, bool) or count < 0:
            raise EvidenceError(f"{field}.{role} must be a non-negative integer")
        out[role] = count
    return out


def _as_role_hists(value: Any, field: str) -> dict[str, dict[int, int]]:
    if not isinstance(value, dict):
        raise EvidenceError(f"{field} must be a JSON object")
    unknown = set(value) - set(LOOKUP_ROLES)
    missing = set(LOOKUP_ROLES) - set(value)
    if unknown or missing:
        raise EvidenceError(f"{field} role mismatch: missing={sorted(missing)}, unknown={sorted(unknown)}")
    return {role: _as_int_hist(value[role], f"{field}.{role}") for role in LOOKUP_ROLES}


def _integer(row: Mapping[str, Any], field: str) -> int:
    value = row.get(field)
    if not isinstance(value, int) or isinstance(value, bool):
        raise EvidenceError(f"{field} must be an integer")
    return value


def _sum_hist(hist: Mapping[int, int]) -> int:
    return sum(hist.values())


def _weighted_sum(hist: Mapping[int, int]) -> int:
    return sum(key * count for key, count in hist.items())


def _weighted_mean(hist: Mapping[int, int]) -> float:
    total = _sum_hist(hist)
    return _weighted_sum(hist) / total if total else math.nan


def _weighted_quantile(hist: Mapping[int, int], quantile: float) -> float:
    if not 0.0 <= quantile <= 1.0:
        raise ValueError(f"quantile must be in [0, 1], got {quantile}")
    total = _sum_hist(hist)
    if total == 0:
        return math.nan
    rank = max(1, math.ceil(quantile * total))
    cumulative = 0
    for value, count in sorted(hist.items()):
        cumulative += count
        if cumulative >= rank:
            return float(value)
    raise AssertionError("unreachable weighted quantile")


def _fraction_at_most(hist: Mapping[int, int], threshold: int) -> float:
    total = _sum_hist(hist)
    if total == 0:
        return math.nan
    return sum(count for value, count in hist.items() if value <= threshold) / total


def _fraction_ratio_at_most(hist: Mapping[int, int], state_count: int, threshold: float) -> float:
    total = _sum_hist(hist)
    if total == 0:
        return math.nan
    return sum(count for value, count in hist.items() if value / state_count <= threshold) / total


def _fraction_ratio_at_least(hist: Mapping[int, int], state_count: int, threshold: float) -> float:
    total = _sum_hist(hist)
    if total == 0:
        return math.nan
    return sum(count for value, count in hist.items() if value / state_count >= threshold) / total


def _ratio(numerator: int | float, denominator: int | float) -> float:
    return numerator / denominator if denominator else math.nan


def _merge_hist(target: dict[int, int], source: Mapping[int, int]) -> None:
    for key, count in source.items():
        target[key] += count


def _merge_role_hists(
    target: dict[str, dict[int, int]], source: Mapping[str, Mapping[int, int]]
) -> None:
    for role in LOOKUP_ROLES:
        _merge_hist(target[role], source[role])


@dataclass(frozen=True)
class TelemetryRow:
    source_file: str
    source_line: int
    raw: dict[str, Any]
    occupancy: dict[int, int]
    iterations: dict[int, int]
    lookups: dict[str, dict[int, int]]
    lookup_hits: dict[str, dict[int, int]]
    level_probes: dict[str, int]
    map_gets: dict[str, int]
    map_bucket_present: dict[str, int]

    @property
    def state_count(self) -> int:
        return int(self.raw["nfaStateCount"])

    @property
    def run_id(self) -> str:
        return str(self.raw["runId"])

    @property
    def query_sequence(self) -> int:
        return int(self.raw["querySequence"])


def validate_row(
    raw: dict[str, Any],
    source_file: str,
    source_line: int,
    *,
    expected_baseline: str,
    allow_unknown_operations: bool,
) -> TelemetryRow:
    where = f"{source_file}:{source_line}"

    def fail(message: str) -> None:
        raise EvidenceError(f"{where}: {message}")

    try:
        schema_version = _integer(raw, "schemaVersion")
        state_count = _integer(raw, "nfaStateCount")
        created = _integer(raw, "createdBuckets")
        committed = _integer(raw, "committedBuckets")
        uncommitted = _integer(raw, "uncommittedBuckets")
        active_slots = _integer(raw, "activeSlots")
        dense_slots = _integer(raw, "denseSlots")
        unused_slots = _integer(raw, "unusedDenseSlots")
        full_iterations = _integer(raw, "fullIterations")
        iteration_slots = _integer(raw, "iterationSlotsScanned")
        iteration_active = _integer(raw, "iterationActiveStates")
        iteration_null = _integer(raw, "iterationNullSlots")
        unknown_operations = _integer(raw, "unknownBucketOperations")
        occupancy = _as_int_hist(raw.get("occupancyHistogram"), "occupancyHistogram")
        iterations = _as_int_hist(raw.get("iterationsByOccupancy"), "iterationsByOccupancy")
        lookups = _as_role_hists(
            raw.get("bucketLookupsByRoleAndOccupancy"), "bucketLookupsByRoleAndOccupancy"
        )
        lookup_hits = _as_role_hists(
            raw.get("bucketLookupHitsByRoleAndOccupancy"),
            "bucketLookupHitsByRoleAndOccupancy",
        )
        level_probes = _as_role_counts(raw.get("levelProbesByRole"), "levelProbesByRole")
        map_gets = _as_role_counts(raw.get("mapGetsByRole"), "mapGetsByRole")
        map_bucket_present = _as_role_counts(
            raw.get("mapBucketPresentByRole"), "mapBucketPresentByRole"
        )
    except EvidenceError as exc:
        fail(str(exc))

    if schema_version != SCHEMA_VERSION:
        fail(f"schemaVersion={schema_version}, expected {SCHEMA_VERSION}")
    if raw.get("evidence") != "MEASURED":
        fail("evidence must be MEASURED")
    if raw.get("causalBaselineSha") != expected_baseline:
        fail(
            "causalBaselineSha="
            f"{raw.get('causalBaselineSha')!r}, expected {expected_baseline!r}"
        )
    if state_count <= 0:
        fail(f"nfaStateCount must be positive, got {state_count}")
    if not isinstance(raw.get("runId"), str) or not raw["runId"]:
        fail("runId must be a non-empty string")
    if _integer(raw, "querySequence") <= 0:
        fail("querySequence must be positive")
    if raw.get("searchMode") not in {"Unidirectional", "Bidirectional"}:
        fail(f"unsupported searchMode {raw.get('searchMode')!r}")

    if any(key > state_count for key in occupancy):
        fail("occupancyHistogram contains k > nfaStateCount")
    if _sum_hist(occupancy) != created:
        fail(
            f"occupancy count {_sum_hist(occupancy)} does not equal createdBuckets {created}"
        )
    if committed + uncommitted != created:
        fail(
            f"committedBuckets + uncommittedBuckets={committed + uncommitted}, "
            f"createdBuckets={created}"
        )
    if _weighted_sum(occupancy) != active_slots:
        fail(
            f"occupancy weighted sum {_weighted_sum(occupancy)} "
            f"does not equal activeSlots {active_slots}"
        )
    if created * state_count != dense_slots:
        fail(
            f"createdBuckets*nfaStateCount={created * state_count}, "
            f"denseSlots={dense_slots}"
        )
    if dense_slots - active_slots != unused_slots:
        fail(
            f"denseSlots-activeSlots={dense_slots - active_slots}, "
            f"unusedDenseSlots={unused_slots}"
        )
    if _sum_hist(iterations) != full_iterations:
        fail(
            f"iteration histogram count {_sum_hist(iterations)}, "
            f"fullIterations={full_iterations}"
        )
    if _weighted_sum(iterations) != iteration_active:
        fail(
            f"iteration histogram weighted sum {_weighted_sum(iterations)}, "
            f"iterationActiveStates={iteration_active}"
        )
    if full_iterations * state_count != iteration_slots:
        fail(
            f"fullIterations*nfaStateCount={full_iterations * state_count}, "
            f"iterationSlotsScanned={iteration_slots}"
        )
    if iteration_slots - iteration_active != iteration_null:
        fail(
            f"iterationSlotsScanned-iterationActiveStates="
            f"{iteration_slots - iteration_active}, iterationNullSlots={iteration_null}"
        )

    for role in LOOKUP_ROLES:
        if any(key > state_count for key in lookups[role]):
            fail(f"lookup histogram {role} contains k > nfaStateCount")
        if any(key > state_count for key in lookup_hits[role]):
            fail(f"lookup-hit histogram {role} contains k > nfaStateCount")
        if _sum_hist(lookup_hits[role]) > _sum_hist(lookups[role]):
            fail(f"{role} lookup hits exceed lookups")
        if map_gets[role] > level_probes[role]:
            fail(f"{role} map gets exceed level probes")
        if map_bucket_present[role] > map_gets[role]:
            fail(f"{role} bucket-present count exceeds map gets")

    if not allow_unknown_operations and unknown_operations != 0:
        fail(f"unknownBucketOperations={unknown_operations}; expected zero")

    return TelemetryRow(
        source_file=source_file,
        source_line=source_line,
        raw=raw,
        occupancy=occupancy,
        iterations=iterations,
        lookups=lookups,
        lookup_hits=lookup_hits,
        level_probes=level_probes,
        map_gets=map_gets,
        map_bucket_present=map_bucket_present,
    )


def load_rows(
    paths: Sequence[Path],
    *,
    expected_baseline: str,
    allow_unknown_operations: bool,
) -> list[TelemetryRow]:
    rows: list[TelemetryRow] = []
    for path in paths:
        with path.open("r", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    raw = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise EvidenceError(f"{path}:{line_number}: invalid JSON: {exc}") from exc
                if not isinstance(raw, dict):
                    raise EvidenceError(f"{path}:{line_number}: row must be a JSON object")
                rows.append(
                    validate_row(
                        raw,
                        str(path),
                        line_number,
                        expected_baseline=expected_baseline,
                        allow_unknown_operations=allow_unknown_operations,
                    )
                )
    if not rows:
        raise EvidenceError("no non-empty telemetry rows found")
    return rows


def _flatten_lookup_hist(row: TelemetryRow) -> dict[int, int]:
    out: dict[int, int] = defaultdict(int)
    for role in LOOKUP_ROLES:
        _merge_hist(out, row.lookups[role])
    return dict(out)


def summarize_row(row: TelemetryRow) -> dict[str, Any]:
    raw = row.raw
    state_count = row.state_count
    occupancy = row.occupancy
    lookups = _flatten_lookup_hist(row)
    iterations = row.iterations
    bucket_count = _sum_hist(occupancy)
    lookup_count = _sum_hist(lookups)
    iteration_count = _sum_hist(iterations)
    active_slots = _integer(raw, "activeSlots")
    dense_slots = _integer(raw, "denseSlots")

    summary: dict[str, Any] = {
        "source_file": row.source_file,
        "source_line": row.source_line,
        "run_id": row.run_id,
        "query_sequence": row.query_sequence,
        "search_mode": raw["searchMode"],
        "S": state_count,
        "buckets": bucket_count,
        "committed_buckets": _integer(raw, "committedBuckets"),
        "uncommitted_buckets": _integer(raw, "uncommittedBuckets"),
        "mean_k": _weighted_mean(occupancy),
        "median_k": _weighted_quantile(occupancy, 0.50),
        "p75_k": _weighted_quantile(occupancy, 0.75),
        "p90_k": _weighted_quantile(occupancy, 0.90),
        "p95_k": _weighted_quantile(occupancy, 0.95),
        "p99_k": _weighted_quantile(occupancy, 0.99),
        "mean_occupancy": _ratio(active_slots, dense_slots),
        "fraction_k_1": _fraction_at_most(occupancy, 1),
        "fraction_k_2": _fraction_at_most(occupancy, 2),
        "fraction_k_le_4": _fraction_at_most(occupancy, 4),
        "fraction_k_le_8": _fraction_at_most(occupancy, 8),
        "fraction_rho_le_005": _fraction_ratio_at_most(occupancy, state_count, 0.05),
        "fraction_rho_le_010": _fraction_ratio_at_most(occupancy, state_count, 0.10),
        "fraction_rho_le_025": _fraction_ratio_at_most(occupancy, state_count, 0.25),
        "fraction_rho_ge_050": _fraction_ratio_at_least(occupancy, state_count, 0.50),
        "fraction_rho_ge_075": _fraction_ratio_at_least(occupancy, state_count, 0.75),
        "contiguous_fraction": _ratio(_integer(raw, "contiguousBuckets"), bucket_count),
        "low_contiguous_fraction": _ratio(
            _integer(raw, "lowContiguousBuckets"), bucket_count
        ),
        "lookup_count": lookup_count,
        "lookup_weighted_mean_k": _weighted_mean(lookups),
        "lookup_weighted_mean_occupancy": _ratio(_weighted_mean(lookups), state_count),
        "iteration_count": iteration_count,
        "iteration_weighted_mean_k": _weighted_mean(iterations),
        "iteration_weighted_mean_occupancy": _ratio(
            _weighted_mean(iterations), state_count
        ),
        "iteration_scan_amplification": _ratio(
            _integer(raw, "iterationSlotsScanned"),
            _integer(raw, "iterationActiveStates"),
        ),
        "iteration_null_fraction": _ratio(
            _integer(raw, "iterationNullSlots"),
            _integer(raw, "iterationSlotsScanned"),
        ),
        "level_probes": sum(row.level_probes.values()),
        "map_gets": sum(row.map_gets.values()),
        "bucket_present_map_gets": sum(row.map_bucket_present.values()),
        "unknown_bucket_operations": _integer(raw, "unknownBucketOperations"),
        "open_level_count": _integer(raw, "openLevelCount"),
        "open_level_buckets": _integer(raw, "openLevelBuckets"),
    }

    for role in LOOKUP_ROLES:
        role_lookups = _sum_hist(row.lookups[role])
        role_hits = _sum_hist(row.lookup_hits[role])
        summary[f"{role.lower()}_lookups"] = role_lookups
        summary[f"{role.lower()}_lookup_hit_rate"] = _ratio(role_hits, role_lookups)

    writes = _as_int_hist(raw["writesByOccupancy"], "writesByOccupancy")
    duplicate_writes = _as_int_hist(
        raw["duplicateWritesByOccupancy"], "duplicateWritesByOccupancy"
    )
    insertion_transitions = _as_int_hist(
        raw["insertionTransitionsByOccupancy"], "insertionTransitionsByOccupancy"
    )
    nondecreasing = _as_int_hist(
        raw["nondecreasingInsertionsByOccupancy"],
        "nondecreasingInsertionsByOccupancy",
    )
    adjacent = _as_int_hist(
        raw["adjacentInsertionsByOccupancy"], "adjacentInsertionsByOccupancy"
    )
    summary["writes"] = _sum_hist(writes)
    summary["duplicate_write_fraction"] = _ratio(
        _sum_hist(duplicate_writes), _sum_hist(writes)
    )
    summary["nondecreasing_insertion_fraction"] = _ratio(
        _sum_hist(nondecreasing), _sum_hist(insertion_transitions)
    )
    summary["adjacent_insertion_fraction"] = _ratio(
        _sum_hist(adjacent), _sum_hist(insertion_transitions)
    )
    return summary


def aggregate_rows(rows: Sequence[TelemetryRow]) -> dict[str, Any]:
    occupancy: dict[int, int] = defaultdict(int)
    iterations: dict[int, int] = defaultdict(int)
    lookups: dict[str, dict[int, int]] = {
        role: defaultdict(int) for role in LOOKUP_ROLES
    }
    lookup_hits: dict[str, dict[int, int]] = {
        role: defaultdict(int) for role in LOOKUP_ROLES
    }
    level_probes = {role: 0 for role in LOOKUP_ROLES}
    map_gets = {role: 0 for role in LOOKUP_ROLES}
    map_bucket_present = {role: 0 for role in LOOKUP_ROLES}

    totals: dict[str, int] = defaultdict(int)
    scalar_fields = (
        "createdBuckets",
        "committedBuckets",
        "uncommittedBuckets",
        "activeSlots",
        "denseSlots",
        "unusedDenseSlots",
        "fullIterations",
        "iterationSlotsScanned",
        "iterationActiveStates",
        "iterationNullSlots",
        "contiguousBuckets",
        "lowContiguousBuckets",
        "unknownBucketOperations",
        "openLevelCount",
        "openLevelBuckets",
    )

    occupancy_ratio_buckets = 0
    ratio_threshold_counts = {
        "rho_le_005": 0,
        "rho_le_010": 0,
        "rho_le_025": 0,
        "rho_ge_050": 0,
        "rho_ge_075": 0,
    }

    for row in rows:
        _merge_hist(occupancy, row.occupancy)
        _merge_hist(iterations, row.iterations)
        _merge_role_hists(lookups, row.lookups)
        _merge_role_hists(lookup_hits, row.lookup_hits)
        for role in LOOKUP_ROLES:
            level_probes[role] += row.level_probes[role]
            map_gets[role] += row.map_gets[role]
            map_bucket_present[role] += row.map_bucket_present[role]
        for field in scalar_fields:
            totals[field] += _integer(row.raw, field)

        for k, count in row.occupancy.items():
            occupancy_ratio_buckets += count
            rho = k / row.state_count
            if rho <= 0.05:
                ratio_threshold_counts["rho_le_005"] += count
            if rho <= 0.10:
                ratio_threshold_counts["rho_le_010"] += count
            if rho <= 0.25:
                ratio_threshold_counts["rho_le_025"] += count
            if rho >= 0.50:
                ratio_threshold_counts["rho_ge_050"] += count
            if rho >= 0.75:
                ratio_threshold_counts["rho_ge_075"] += count

    all_lookups: dict[int, int] = defaultdict(int)
    for role in LOOKUP_ROLES:
        _merge_hist(all_lookups, lookups[role])

    result: dict[str, Any] = {
        "rows": len(rows),
        "run_ids": sorted({row.run_id for row in rows}),
        "state_counts": sorted({row.state_count for row in rows}),
        "buckets": totals["createdBuckets"],
        "active_slots": totals["activeSlots"],
        "dense_slots": totals["denseSlots"],
        "unused_dense_slots": totals["unusedDenseSlots"],
        "mean_occupancy": _ratio(totals["activeSlots"], totals["denseSlots"]),
        "fraction_k_1": _fraction_at_most(occupancy, 1),
        "fraction_k_2": _fraction_at_most(occupancy, 2),
        "fraction_k_le_4": _fraction_at_most(occupancy, 4),
        "fraction_k_le_8": _fraction_at_most(occupancy, 8),
        "fraction_rho_le_005": _ratio(
            ratio_threshold_counts["rho_le_005"], occupancy_ratio_buckets
        ),
        "fraction_rho_le_010": _ratio(
            ratio_threshold_counts["rho_le_010"], occupancy_ratio_buckets
        ),
        "fraction_rho_le_025": _ratio(
            ratio_threshold_counts["rho_le_025"], occupancy_ratio_buckets
        ),
        "fraction_rho_ge_050": _ratio(
            ratio_threshold_counts["rho_ge_050"], occupancy_ratio_buckets
        ),
        "fraction_rho_ge_075": _ratio(
            ratio_threshold_counts["rho_ge_075"], occupancy_ratio_buckets
        ),
        "mean_k": _weighted_mean(occupancy),
        "median_k": _weighted_quantile(occupancy, 0.50),
        "p90_k": _weighted_quantile(occupancy, 0.90),
        "p95_k": _weighted_quantile(occupancy, 0.95),
        "p99_k": _weighted_quantile(occupancy, 0.99),
        "lookup_count": _sum_hist(all_lookups),
        "lookup_weighted_mean_k": _weighted_mean(all_lookups),
        "iteration_count": totals["fullIterations"],
        "iteration_weighted_mean_k": _weighted_mean(iterations),
        "iteration_scan_amplification": _ratio(
            totals["iterationSlotsScanned"], totals["iterationActiveStates"]
        ),
        "iteration_null_fraction": _ratio(
            totals["iterationNullSlots"], totals["iterationSlotsScanned"]
        ),
        "contiguous_fraction": _ratio(
            totals["contiguousBuckets"], totals["createdBuckets"]
        ),
        "low_contiguous_fraction": _ratio(
            totals["lowContiguousBuckets"], totals["createdBuckets"]
        ),
        "unknown_bucket_operations": totals["unknownBucketOperations"],
        "level_probes": sum(level_probes.values()),
        "map_gets": sum(map_gets.values()),
        "bucket_present_map_gets": sum(map_bucket_present.values()),
        "occupancy_histogram": dict(sorted(occupancy.items())),
        "iterations_by_occupancy": dict(sorted(iterations.items())),
        "lookups_by_role_and_occupancy": {
            role: dict(sorted(lookups[role].items())) for role in LOOKUP_ROLES
        },
        "lookup_hits_by_role_and_occupancy": {
            role: dict(sorted(lookup_hits[role].items())) for role in LOOKUP_ROLES
        },
    }
    return result


def _fmt(value: Any) -> str:
    if isinstance(value, float):
        if math.isnan(value):
            return ""
        return f"{value:.6f}"
    if isinstance(value, (dict, list)):
        return json.dumps(value, sort_keys=True, separators=(",", ":"))
    return str(value)


def write_csv(path: Path, summaries: Sequence[Mapping[str, Any]]) -> None:
    fieldnames: list[str] = []
    seen: set[str] = set()
    for summary in summaries:
        for field in summary:
            if field not in seen:
                fieldnames.append(field)
                seen.add(field)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for summary in summaries:
            writer.writerow({field: _fmt(summary.get(field, "")) for field in fieldnames})


def _percent(value: Any) -> str:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return "n/a"
    if isinstance(value, float) and math.isnan(value):
        return "n/a"
    return f"{100.0 * float(value):.2f}%"


def write_markdown(
    path: Path, summaries: Sequence[Mapping[str, Any]], aggregate: Mapping[str, Any]
) -> None:
    lines = [
        "# PPBFS P2 concrete state-bucket summary",
        "",
        "Evidence: `DERIVED` from validated schema-v1 JSONL rows.",
        "",
        "## Aggregate",
        "",
        f"- Rows: **{aggregate['rows']}**",
        f"- Run IDs: `{', '.join(aggregate['run_ids'])}`",
        f"- NFA state counts: `{', '.join(map(str, aggregate['state_counts']))}`",
        f"- Concrete buckets: **{aggregate['buckets']:,}**",
        f"- Mean occupancy: **{_percent(aggregate['mean_occupancy'])}**",
        f"- Buckets with `k <= 2`: **{_percent(aggregate['fraction_k_2'])}**",
        f"- Buckets with `k <= 4`: **{_percent(aggregate['fraction_k_le_4'])}**",
        f"- Buckets with `k/S <= 10%`: **{_percent(aggregate['fraction_rho_le_010'])}**",
        f"- Buckets with `k/S >= 75%`: **{_percent(aggregate['fraction_rho_ge_075'])}**",
        f"- Median / p95 / p99 `k`: **{_fmt(aggregate['median_k'])} / "
        f"{_fmt(aggregate['p95_k'])} / {_fmt(aggregate['p99_k'])}**",
        f"- Full frontier iterations: **{aggregate['iteration_count']:,}**",
        f"- Null share of scanned iteration slots: "
        f"**{_percent(aggregate['iteration_null_fraction'])}**",
        f"- Iteration scan amplification (`slots / active states`): "
        f"**{_fmt(aggregate['iteration_scan_amplification'])}x**",
        f"- Bucket lookups: **{aggregate['lookup_count']:,}**",
        f"- Unknown bucket operations: **{aggregate['unknown_bucket_operations']:,}**",
        "",
        "## Per query",
        "",
        "| run | query | mode | S | buckets | mean k | median k | p95 k | mean occupancy | "
        "k<=2 | iteration nulls | scan amplification | lookups |",
        "| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for summary in summaries:
        lines.append(
            "| {run_id} | {query_sequence} | {search_mode} | {S} | {buckets} | "
            "{mean_k} | {median_k} | {p95_k} | {mean_occupancy} | {fraction_k_2} | "
            "{iteration_null_fraction} | {iteration_scan_amplification} | {lookup_count} |".format(
                run_id=summary["run_id"],
                query_sequence=summary["query_sequence"],
                search_mode=summary["search_mode"],
                S=summary["S"],
                buckets=summary["buckets"],
                mean_k=_fmt(summary["mean_k"]),
                median_k=_fmt(summary["median_k"]),
                p95_k=_fmt(summary["p95_k"]),
                mean_occupancy=_percent(summary["mean_occupancy"]),
                fraction_k_2=_percent(summary["fraction_k_2"]),
                iteration_null_fraction=_percent(summary["iteration_null_fraction"]),
                iteration_scan_amplification=_fmt(
                    summary["iteration_scan_amplification"]
                ),
                lookup_count=summary["lookup_count"],
            )
        )
    lines.extend(
        [
            "",
            "## Qualification notes",
            "",
            "- This report characterizes the instrumented baseline only.",
            "- Instrumentation rows are not valid latency, allocation, GC, tracked-memory, "
            "or memory-limit evidence.",
            "- Cross-workload aggregation of absolute `k` is descriptive when `S` differs; "
            "ratio thresholds remain normalized per row.",
            "- Candidate selection requires workload labels and result/operator qualification "
            "from the workload manifest.",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8")


def resolve_inputs(patterns: Sequence[str]) -> list[Path]:
    paths: list[Path] = []
    seen: set[Path] = set()
    for pattern in patterns:
        matches = [Path(match) for match in glob.glob(pattern, recursive=True)]
        if not matches and Path(pattern).is_file():
            matches = [Path(pattern)]
        for path in sorted(matches):
            resolved = path.resolve()
            if resolved not in seen and path.is_file():
                paths.append(path)
                seen.add(resolved)
    if not paths:
        raise EvidenceError("no input files matched")
    return paths


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate and summarize PPBFS P2 bucket telemetry JSONL"
    )
    parser.add_argument(
        "--input",
        nargs="+",
        required=True,
        help="JSONL paths or glob patterns; quote recursive globs in the shell",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        required=True,
        help="Directory for summary.csv, aggregate.json, and SUMMARY.md",
    )
    parser.add_argument(
        "--expected-baseline",
        default=CAUSAL_BASELINE_SHA,
        help="Required causalBaselineSha",
    )
    parser.add_argument(
        "--allow-unknown-operations",
        action="store_true",
        help="Retain rows with non-zero unknownBucketOperations",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        paths = resolve_inputs(args.input)
        rows = load_rows(
            paths,
            expected_baseline=args.expected_baseline,
            allow_unknown_operations=args.allow_unknown_operations,
        )
        summaries = [summarize_row(row) for row in rows]
        aggregate = aggregate_rows(rows)
        args.output_dir.mkdir(parents=True, exist_ok=True)
        write_csv(args.output_dir / "summary.csv", summaries)
        (args.output_dir / "aggregate.json").write_text(
            json.dumps(aggregate, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        write_markdown(args.output_dir / "SUMMARY.md", summaries, aggregate)
    except (EvidenceError, OSError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
