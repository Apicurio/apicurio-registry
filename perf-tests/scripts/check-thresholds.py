#!/usr/bin/env python3
"""
Compares a Gatling run's global stats (js/stats.js) against perf-tests/baseline.json and prints a
GitHub Actions job summary. This intentionally never fails the workflow on its own (regressions
are surfaced via Slack/summary for review, not treated as a hard merge-blocking gate, since
perf-main only runs after a merge to main has already happened) - the workflow's pass/fail state
is instead driven by Gatling's own built-in assertions (see RegistryApiSimulation) and the Kafka
load generator's exit code.

Gatling's HTML report does not emit plain JSON: js/stats.js is a JS file of the form
"var stats = { ... contents: { <per-request stats> } };" where the object literal uses a mix of
bare and quoted keys, so it isn't valid JSON as-is. Rather than write a full JS-object parser
(only used for one report file, and brittle across Gatling versions), this script extracts the
handful of metrics it needs from the *global* stats block - the portion of the file before the
first "contents:" marker - with targeted regexes. This was validated against a real local
Gatling 3.13.5 run's js/stats.js output.

Usage: check-thresholds.py <path-to-js/stats.js> <path-to-baseline.json>
"""
import json
import os
import re
import sys


def extract_metric(text, key):
    match = re.search(r'"' + re.escape(key) + r'":\s*{\s*"total":\s*"?([\d.\-]+)"?', text)
    if not match or match.group(1) == "-":
        return None
    return float(match.group(1))


def main():
    if len(sys.argv) != 3:
        print("Usage: check-thresholds.py <js/stats.js> <baseline.json>", file=sys.stderr)
        return 2

    stats_path, baseline_path = sys.argv[1], sys.argv[2]
    with open(stats_path) as f:
        content = f.read()
    with open(baseline_path) as f:
        baseline = json.load(f)

    # Only look at the global aggregate block (before per-request breakdowns in "contents:").
    global_block = content.split("contents:", 1)[0]

    mean_ms = extract_metric(global_block, "meanResponseTime")
    p95_ms = extract_metric(global_block, "percentiles3")
    p99_ms = extract_metric(global_block, "percentiles4")
    total = extract_metric(global_block, "numberOfRequests")
    ko_match = re.search(r'"numberOfRequests":\s*{\s*"total":\s*"?([\d.\-]+)"?,\s*"ok":\s*"?([\d.\-]+)"?,\s*"ko":\s*"?([\d.\-]+)"?', global_block)
    ko = float(ko_match.group(3)) if ko_match else 0.0
    failed_percent = (100.0 * ko / total) if total else 0.0

    tolerance = baseline.get("regressionToleranceFactor", 1.25)

    rows = [
        ("Mean response time (ms)", mean_ms, baseline.get("meanResponseTimeMs")),
        ("p95 response time (ms)", p95_ms, baseline.get("p95ResponseTimeMs")),
        ("p99 response time (ms)", p99_ms, baseline.get("p99ResponseTimeMs")),
    ]

    regressions = []
    lines = ["| Metric | Observed | Baseline | Status |", "| --- | --- | --- | --- |"]
    for name, observed, base in rows:
        if observed is None or base is None:
            lines.append(f"| {name} | {observed} | {base} | unavailable |")
            continue
        threshold = base * tolerance
        if observed > threshold:
            regressions.append(name)
            status = f"REGRESSION (> {threshold:.0f}ms)"
        else:
            status = "ok"
        lines.append(f"| {name} | {observed:.0f} | {base} | {status} |")

    max_failed = baseline.get("maxFailedRequestsPercent", 100)
    failed_status = "REGRESSION" if failed_percent > max_failed else "ok"
    if failed_status == "REGRESSION":
        regressions.append("Failed requests")
    lines.append(f"| Failed requests (%) | {failed_percent:.2f} | {max_failed} | {failed_status} |")

    summary = "\n".join(lines)
    print(summary)

    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a") as f:
            f.write("## Performance test results\n\n")
            f.write(summary)
            f.write("\n")
            if regressions:
                f.write(f"\n**Potential regressions detected in:** {', '.join(regressions)}\n")

    # Non-zero exit is informational only; the caller (perf-main.yaml) does not treat it as a
    # hard failure - see module docstring above.
    return 1 if regressions else 0


if __name__ == "__main__":
    sys.exit(main())
