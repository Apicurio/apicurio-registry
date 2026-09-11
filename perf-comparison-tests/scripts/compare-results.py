#!/usr/bin/env python3
import csv
import json
import pathlib
import random
import re
import statistics
import sys


def metric(block, key, field="total"):
    match = re.search(r'"' + re.escape(key) + r'":\s*\{[^}]*"' + field + r'":\s*"?([\d.\-]+)', block)
    return None if not match or match.group(1) == "-" else float(match.group(1))


def measured_block(text):
    matches = list(re.finditer(r'type:\s*"REQUEST",\s*name:\s*"Measured [^"]+"', text))
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one measured request block, found {len(matches)}")
    start = matches[0].start()
    end = text.find("\n    }\n}", start)
    if end < 0:
        raise ValueError("Could not find the end of the measured request block")
    return text[start:end]


def parse_run(stats_path):
    run_dir = stats_path.parents[3]
    metadata = json.loads((run_dir / "deployment-metadata.json").read_text())
    block = measured_block(stats_path.read_text())
    total = metric(block, "numberOfRequests")
    ok = metric(block, "numberOfRequests", "ok")
    ko = metric(block, "numberOfRequests", "ko")
    duration = metadata.get("duration")
    if not isinstance(duration, (int, float)) or duration <= 0:
        raise ValueError(f"Invalid measured duration for {run_dir}: {duration}")
    return {
        **metadata,
        "total": int(total),
        "ok": int(ok),
        "ko": int(ko),
        "failedPercent": 100.0 * ko / total if total else 0.0,
        "rps": ok / duration,
        "gatlingSimulationWindowRps": metric(block, "meanNumberOfRequestsPerSecond", "ok"),
        "meanMs": metric(block, "meanResponseTime", "ok"),
        "p50Ms": metric(block, "percentiles1", "ok"),
        "p95Ms": metric(block, "percentiles2", "ok"),
        "p99Ms": metric(block, "percentiles3", "ok"),
        "p999Ms": metric(block, "percentiles4", "ok"),
    }


def bootstrap_median_ci(values, samples=10000):
    if len(values) == 1:
        return values[0], values[0]
    randomizer = random.Random(0)
    medians = sorted(statistics.median(randomizer.choices(values, k=len(values))) for _ in range(samples))
    return medians[int(samples * 0.025)], medians[int(samples * 0.975)]


def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "results")
    output = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else root / "comparison")
    runs = [parse_run(path) for path in root.glob("**/gatling/*/js/stats.js")]
    if not runs:
        raise SystemExit("No Gatling stats.js files found")
    output.mkdir(parents=True, exist_ok=True)
    (output / "comparison.json").write_text(json.dumps(runs, indent=2) + "\n")
    fields = list(runs[0])
    with (output / "comparison.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(runs)

    lines = ["# Product-neutral schema registry comparison", "", "| Operation | Product | Runs | Median successful measured-window RPS (95% CI) | Median p99 ms (95% CI) | Median p99.9 ms | Median failures |", "| --- | --- | ---: | ---: | ---: | ---: | ---: |"]
    for operation, product in sorted({(run["operation"], run["product"]) for run in runs}):
        group = [run for run in runs if run["operation"] == operation and run["product"] == product]
        comparable = {(run["users"], run["warmup"], run["duration"], run["seeds"], run["replicas"]) for run in group}
        if len(comparable) != 1:
            raise ValueError(f"Incomparable run parameters for {operation}/{product}: {sorted(comparable)}")
        rps = [run["rps"] for run in group]
        p99 = [run["p99Ms"] for run in group]
        rps_ci = bootstrap_median_ci(rps)
        p99_ci = bootstrap_median_ci(p99)
        lines.append(f'| {operation} | {product} | {len(group)} | {statistics.median(rps):.2f} ({rps_ci[0]:.2f}-{rps_ci[1]:.2f}) | '
                     f'{statistics.median(p99):.0f} ({p99_ci[0]:.0f}-{p99_ci[1]:.0f}) | '
                     f'{statistics.median(r["p999Ms"] for r in group):.0f} | '
                     f'{statistics.median(r["failedPercent"] for r in group):.3f}% |')
    lines.extend(["", "Results are comparable only when deployment metadata, operation, resource envelope, durability profile, authentication, TLS, dataset, and runner hardware are identical."])
    report = "\n".join(lines) + "\n"
    (output / "comparison.md").write_text(report)
    print(report)


if __name__ == "__main__":
    main()
