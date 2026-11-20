"""Parser for the Global Information block Gatling prints at the end of a run.

Two perf-tests scripts need these numbers - check-external-validity.py (did the run
actually exercise the registry?) and check-external-baseline.py (how did it compare to
the recorded baseline?) - so the parsing lives here rather than being duplicated and
allowed to drift.

Note the Gatling HTML report's js/stats.js is parsed separately by check-thresholds.py:
that is a different input format for the in-cluster run, and is deliberately left alone.

The block looks like this, with Total / OK / KO columns:

    ---- Global Information ------------------------|---Total---|-----OK----|----KO----
    > request count                                 |    92,580 |    92,578 |         2
    > mean response time (ms)                       |       102 |       101 |    60,001
    > response time 95th percentile (ms)            |       116 |       116 |    60,001
    > mean throughput (rps)                         |     462.9 |    462.89 |      0.01
    ---- Response Time Distribution ----------------------------------------------------
    > OK: t < 800 ms                                                     92,578   (100%)
    > KO                                                                      2     (0%)
    ---- Errors -------------------------------------------------------------------------
    > j.n.ConnectException: ... Connection refused                   1,404,879 (99.92%)

A dash means "no such requests" (for example the KO column of a run with no failures) and
is returned as None rather than zero, since those are different facts.
"""

import re

_ROW = r"^>\s*{label}\s*\|\s*(\S+)\s*\|\s*(\S+)\s*\|\s*(\S+)\s*$"

KO_PERCENT = re.compile(r"^>\s*KO\s+[\d,]+\s+\(\s*([\d.]+)%\)", re.MULTILINE)
ERROR_LINE = re.compile(r"^>\s*(\S.*?)\s{2,}([\d,]+)\s+\(\s*[\d.]+%\)", re.MULTILINE)

# Failures meaning the target was not reachable, as opposed to it answering with something
# unwanted. A 500 is the registry being unhappy; an ECONNREFUSED is the registry not being
# there, and only the latter invalidates a measurement.
UNREACHABLE_MARKERS = (
    "Connection refused",
    "Connection reset by peer",
    "Premature close",
    "connection timed out",
    "No route to host",
)


def _number(text):
    """Gatling prints thousands separators, and '-' where a column has no data."""
    text = text.strip()
    if text in ("-", ""):
        return None
    try:
        return float(text.replace(",", ""))
    except ValueError:
        return None


def _row(text, label):
    match = re.search(_ROW.format(label=re.escape(label)), text, re.MULTILINE)
    if not match:
        return (None, None, None)
    return tuple(_number(g) for g in match.groups())


def parse(log_text):
    """Return the run's headline metrics, or None if there is no parseable summary."""
    if "Global Information" not in log_text:
        return None

    total, ok, ko = _row(log_text, "request count")
    if total is None:
        return None

    ko_percent_match = KO_PERCENT.search(log_text)
    if ko_percent_match is not None:
        ko_percent = float(ko_percent_match.group(1))
    else:
        ko_percent = (100.0 * ko / total) if (ko is not None and total) else 0.0

    unreachable = 0
    errors_block = log_text.rsplit("---- Errors", 1)[-1] if "---- Errors" in log_text else ""
    for label, count in ERROR_LINE.findall(errors_block):
        if any(marker in label for marker in UNREACHABLE_MARKERS):
            unreachable += int(count.replace(",", ""))

    # The OK column is what matters for a baseline: throughput and latency of requests that
    # actually succeeded. Including failures would let a run "improve" by failing faster.
    return {
        "total": int(total),
        "ok": int(ok) if ok is not None else 0,
        "ko": int(ko) if ko is not None else 0,
        "ko_percent": ko_percent,
        "unreachable": unreachable,
        "throughput_ok": _row(log_text, "mean throughput (rps)")[1],
        "mean_ms_ok": _row(log_text, "mean response time (ms)")[1],
        "p95_ms_ok": _row(log_text, "response time 95th percentile (ms)")[1],
        "p99_ms_ok": _row(log_text, "response time 99th percentile (ms)")[1],
    }
