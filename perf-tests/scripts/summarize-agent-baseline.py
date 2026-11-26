#!/usr/bin/env python3
"""
Summarizes an AgentDiscoverySimulation run (the agent registry scale baseline, #11093) as a
Markdown table: per request, the count, failures and mean/p95/p99/max response times, plus MCP
import throughput (tools per second, from the tool count in the request name and the mean import
time). Appends the table to $GITHUB_STEP_SUMMARY when set, and always prints it.

These numbers are recorded as evidence for the graduation plan, not compared with thresholds, so
this script never fails the workflow because of what it reads.

Like check-thresholds.py, it reads Gatling's js/stats.js, which is a JS object literal rather than
JSON, with targeted regexes: the file is split into one block per request at each `name: "..."`.

Usage: summarize-agent-baseline.py <path-to-js/stats.js> [<storage label>]
"""
import os
import re
import sys

METRICS = [("numberOfRequests", "total"), ("meanResponseTime", "total"), ("percentiles3", "total"),
           ("percentiles4", "total"), ("maxResponseTime", "total")]
IMPORT_NAME = re.compile(r"MCP import \((\d+) tools\)")


def metric(block, key, field="total"):
    match = re.search(r'"' + re.escape(key) + r'":\s*{[^}]*?"' + field + r'":\s*"?([\d.\-]+)"?', block)
    if not match or match.group(1) == "-":
        return None
    return float(match.group(1))


def blocks(text):
    """(name, block) for every request, the global "All Requests" block first."""
    starts = [(m.start(), m.group(1)) for m in re.finditer(r'name:\s*"([^"]+)"', text)]
    for i, (start, name) in enumerate(starts):
        end = starts[i + 1][0] if i + 1 < len(starts) else len(text)
        yield name, text[start:end]


def summarize(text, label):
    rows = ["| Request | Count | Failed | Mean ms | p95 ms | p99 ms | Max ms |",
            "|---|---:|---:|---:|---:|---:|---:|"]
    throughput = []
    for name, block in blocks(text):
        count, mean, p95, p99, top = (metric(block, key, field) for key, field in METRICS)
        failed = metric(block, "numberOfRequests", "ko")
        if count is None:
            continue
        cells = [fmt(v) for v in (count, failed, mean, p95, p99, top)]
        rows.append("| " + name + " | " + " | ".join(cells) + " |")
        imported = IMPORT_NAME.search(name)
        if imported and mean:
            tools = int(imported.group(1))
            throughput.append(f"MCP import: {tools} tools in {mean:.0f} ms on average, "
                              f"{tools * 1000 / mean:.0f} tools/s")
    title = "### Agent registry scale baseline" + (f" ({label})" if label else "")
    return "\n".join([title, ""] + rows + [""] + throughput) + "\n"


def fmt(value):
    if value is None:
        return "-"
    return str(int(value)) if value == int(value) else f"{value:.1f}"


def main():
    if len(sys.argv) not in (2, 3):
        print("Usage: summarize-agent-baseline.py <js/stats.js> [<storage label>]", file=sys.stderr)
        return 2
    with open(sys.argv[1]) as f:
        summary = summarize(f.read(), sys.argv[2] if len(sys.argv) == 3 else "")
    print(summary)
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a") as f:
            f.write(summary)
    return 0


if __name__ == "__main__":
    sys.exit(main())
