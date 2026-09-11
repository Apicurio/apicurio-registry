#!/usr/bin/env python3
"""Compare an external load run against perf-tests/external-baseline.json.

This is the trend check for the external high-concurrency validation, and the counterpart
to check-thresholds.py (which does the same job for the in-cluster Gatling run against
baseline.json). Like that script it is *informational*: perf-main only runs after a merge
has already landed, so there is nothing left to block, and throughput on GitHub-hosted
runners varies by roughly 25% run to run. It prints a job summary and exits 0 even when it
reports a regression, unless --strict is passed.

Whether the run was valid at all is a separate, hard-gated question - see
check-external-validity.py. Comparing a collapsed run against a baseline is meaningless, so
this refuses to report on one.

Usage: check-external-baseline.py <console.log> <external-baseline.json> --storage <name>
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gatling_console import parse  # noqa: E402


def _fmt(value):
    if value is None:
        return "n/a"
    return f"{value:,.0f}" if value >= 10 else f"{value:,.2f}"


def evaluate(stats, baseline, storage):
    """Return (rows, regressions). A row is (metric, observed, baseline, status)."""
    per_storage = baseline.get("storage", {}).get(storage)
    if per_storage is None:
        return None, None

    latency_tolerance = baseline.get("regressionToleranceFactor", 1.25)
    throughput_floor = baseline.get("throughputFloorFactor", 0.7)

    rows = []
    regressions = []

    # Throughput regresses downward, so it has its own floor rather than the latency factor.
    observed = stats.get("throughput_ok")
    expected = per_storage.get("throughputRps")
    if observed is None or expected is None:
        rows.append(("Throughput (rps)", observed, expected, "unavailable"))
    else:
        floor = expected * throughput_floor
        if observed < floor:
            regressions.append("Throughput")
            status = f"REGRESSION (< {floor:,.0f})"
        else:
            status = "ok"
        rows.append(("Throughput (rps)", observed, expected, status))

    for label, key, base_key in (
        ("Mean response time (ms)", "mean_ms_ok", "meanResponseTimeMs"),
        ("p95 response time (ms)", "p95_ms_ok", "p95ResponseTimeMs"),
        ("p99 response time (ms)", "p99_ms_ok", "p99ResponseTimeMs"),
    ):
        observed = stats.get(key)
        expected = per_storage.get(base_key)
        if observed is None or expected is None:
            rows.append((label, observed, expected, "unavailable"))
            continue
        threshold = expected * latency_tolerance
        if observed > threshold:
            regressions.append(label)
            status = f"REGRESSION (> {threshold:,.0f})"
        else:
            status = "ok"
        rows.append((label, observed, expected, status))

    return rows, regressions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", help="run-external-load.sh console log")
    parser.add_argument("baseline", help="external-baseline.json")
    parser.add_argument("--storage", required=True, help="storage variant, e.g. postgresql")
    parser.add_argument(
        "--strict",
        action="store_true",
        help="exit non-zero on regression (default: informational, always exits 0)",
    )
    args = parser.parse_args()

    try:
        with open(args.log, encoding="utf-8", errors="replace") as handle:
            stats = parse(handle.read())
        with open(args.baseline, encoding="utf-8") as handle:
            baseline = json.load(handle)
    except OSError as exc:
        print(f"Cannot read input: {exc}")
        return 0 if not args.strict else 1

    if stats is None:
        print("No parseable Gatling summary - skipping baseline comparison.")
        return 0 if not args.strict else 1

    # A collapsed run's numbers are not a measurement, so do not dignify them with a
    # comparison; check-external-validity.py is what fails the job in that case.
    if stats["ko_percent"] > 25.0:
        print(
            f"Run failed {stats['ko_percent']:.2f}% of requests - not comparing against the "
            "baseline, since a collapsed run's throughput is not a measurement. See the "
            "validity check."
        )
        return 0 if not args.strict else 1

    rows, regressions = evaluate(stats, baseline, args.storage)
    if rows is None:
        print(f"No baseline recorded for storage '{args.storage}' - skipping comparison.")
        return 0

    lines = [
        f"## External load baseline ({args.storage})",
        "",
        "| Metric | Observed | Baseline | Status |",
        "| --- | --- | --- | --- |",
    ]
    for label, observed, expected, status in rows:
        lines.append(f"| {label} | {_fmt(observed)} | {_fmt(expected)} | {status} |")
    if regressions:
        lines.append("")
        lines.append(f"**Potential regressions detected in:** {', '.join(regressions)}")

    summary = "\n".join(lines)
    print(summary)

    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as handle:
            handle.write(summary + "\n")

    if regressions and args.strict:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
