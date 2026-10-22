#!/usr/bin/env python3
"""Decide whether an external load run produced a usable measurement.

This is deliberately *not* a performance check - perf-tests/scripts/check-thresholds.py
already compares latency and throughput against the baseline, and those numbers are
informational on GitHub-hosted runners because the hardware varies run to run.

This answers the prior question: did the run actually exercise the registry at all? A run
where most requests never got a TCP connection is not a slow measurement, it is an absent
one, and reporting its "throughput" as a result is worse than reporting nothing - the
figure is dominated by the rate of failed connects. That is exactly what happened when the
external validation ran at 200 concurrent clients on a GitHub runner: ~95% ECONNREFUSED in
bursts on a ~30-40s cycle (a restart loop), published as a throughput number, for weeks,
while the job stayed green.

Exit status is 0 when the run is usable and 1 when it is not, so the calling step fails.
"""

import argparse
import re
import sys

# "> KO       1,406,042 (94.74%)" in Gatling's Response Time Distribution block.
KO_LINE = re.compile(r"^>\s*KO\s+([\d,]+)\s+\(\s*([\d.]+)%\)", re.MULTILINE)
# "> request count   | 1,484,140 |    78,098 | 1,406,042"
REQUEST_COUNT_LINE = re.compile(
    r"^>\s*request count\s*\|\s*([\d,]+)\s*\|\s*([\d,]+)\s*\|\s*([\d,]+)", re.MULTILINE
)
# Gatling echoes each distinct failure with its own share of the total errors.
ERROR_LINE = re.compile(r"^>\s*(\S.*?)\s{2,}([\d,]+)\s+\(\s*[\d.]+%\)", re.MULTILINE)

# Failures that mean the target was not reachable, as opposed to the target answering with
# something we did not want. The distinction matters: a 500 is the registry being unhappy,
# an ECONNREFUSED is the registry not being there.
UNREACHABLE_MARKERS = (
    "Connection refused",
    "Connection reset by peer",
    "Premature close",
    "connection timed out",
    "No route to host",
)


def _int(text):
    return int(text.replace(",", ""))


def parse(log_text):
    ko_match = KO_LINE.search(log_text)
    count_match = REQUEST_COUNT_LINE.search(log_text)
    if not ko_match or not count_match:
        return None

    total, ok, ko = (_int(g) for g in count_match.groups())
    unreachable = 0
    # Only the Errors block matters; take everything after the last "---- Errors" header.
    errors_block = log_text.rsplit("---- Errors", 1)[-1] if "---- Errors" in log_text else ""
    for label, count in ERROR_LINE.findall(errors_block):
        if any(marker in label for marker in UNREACHABLE_MARKERS):
            unreachable += _int(count)

    return {
        "total": total,
        "ok": ok,
        "ko": ko,
        "ko_percent": float(ko_match.group(2)),
        "unreachable": unreachable,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", help="run-external-load.sh console log")
    parser.add_argument(
        "--max-ko-percent",
        type=float,
        default=25.0,
        help="fail above this percentage of failed requests (default: 25)",
    )
    args = parser.parse_args()

    try:
        with open(args.log, encoding="utf-8", errors="replace") as handle:
            log_text = handle.read()
    except OSError as exc:
        print(f"::error::Cannot read {args.log}: {exc}")
        return 1

    stats = parse(log_text)
    if stats is None:
        print("::error::No parseable Gatling summary in the external load log - the run did not complete.")
        return 1

    print("### External load run validity")
    print("")
    print("| metric | value |")
    print("| --- | --- |")
    print(f"| requests | {stats['total']:,} |")
    print(f"| ok | {stats['ok']:,} |")
    print(f"| failed | {stats['ko']:,} ({stats['ko_percent']:.2f}%) |")
    print(f"| of which unreachable (connect/reset/close) | {stats['unreachable']:,} |")
    print(f"| threshold | {args.max_ko_percent:.2f}% |")
    print("")

    if stats["ko_percent"] > args.max_ko_percent:
        detail = ""
        if stats["unreachable"] > stats["ko"] / 2:
            detail = (
                " Most failures are connect-level, so the registry was unreachable for much of"
                " the run rather than merely slow - check the captured diagnostics for restarts."
            )
        print(
            f"::error::External load run is not a usable measurement: {stats['ko_percent']:.2f}%"
            f" of {stats['total']:,} requests failed (threshold {args.max_ko_percent:.2f}%).{detail}"
        )
        return 1

    print(f"Run is usable: {stats['ko_percent']:.2f}% failed requests is within the "
          f"{args.max_ko_percent:.2f}% threshold.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
