import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gatling_console

_HERE = os.path.dirname(os.path.abspath(__file__))
_BASELINE_PATH = os.path.join(_HERE, os.pardir, "external-baseline.json")


def _load_script(name):
    import importlib.util
    path = os.path.join(_HERE, name)
    spec = importlib.util.spec_from_file_location(name.replace("-", "_").rstrip(".py"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


ceb = _load_script("check-external-baseline.py")


HEALTHY_POSTGRESQL = """
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      |    92,580 |    92,578 |         2
> mean response time (ms)                                                            |       102 |       101 |    60,001
> response time 95th percentile (ms)                                                 |       116 |       116 |    60,001
> response time 99th percentile (ms)                                                 |       130 |       130 |    60,001
> mean throughput (rps)                                                              |     462.9 |    462.89 |      0.01
---- Response Time Distribution ----------------------------------------------------------------------------------------
> KO                                                                                                          2     (0%)
"""

# A run with zero failures prints "-" in the KO column rather than 0.
NO_FAILURES = """
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      |   336,923 |   336,923 |         0
> mean response time (ms)                                                            |        28 |        28 |         -
> response time 95th percentile (ms)                                                 |        68 |        68 |         -
> response time 99th percentile (ms)                                                 |        89 |        89 |         -
> mean throughput (rps)                                                              |  1,684.62 |  1,684.62 |         -
---- Response Time Distribution ----------------------------------------------------------------------------------------
> KO                                                                                                          0     (0%)
"""

COLLAPSED = """
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      | 1,396,990 |    59,167 | 1,337,823
> mean response time (ms)                                                            |        23 |       352 |         7
> response time 95th percentile (ms)                                                 |       120 |       357 |        10
> response time 99th percentile (ms)                                                 |       197 |     5,313 |        22
> mean throughput (rps)                                                              |   7,420.7 |    294.36 |  7,030.21
---- Response Time Distribution ----------------------------------------------------------------------------------------
> KO                                                                                                  1,337,823 (95.76%)
---- Errors ------------------------------------------------------------------------------------------------------------
> j.n.ConnectException: finishConnect(..) failed with error(-111): Connection refused                 1,336,886 (99.92%)
> status.find.is(200), but actually found 500                                                               686  (0.05%)
"""


class ConsoleParserTests(unittest.TestCase):

    def test_reads_ok_column_not_total(self):
        stats = gatling_console.parse(HEALTHY_POSTGRESQL)
        # The Total column is 102/462.9; the OK column is what a baseline must use.
        self.assertEqual(stats["mean_ms_ok"], 101.0)
        self.assertEqual(stats["throughput_ok"], 462.89)

    def test_dash_columns_are_none_not_zero(self):
        stats = gatling_console.parse(NO_FAILURES)
        self.assertEqual(stats["ko"], 0)
        self.assertEqual(stats["throughput_ok"], 1684.62)
        self.assertEqual(stats["mean_ms_ok"], 28.0)

    def test_collapsed_run_attributes_unreachable_separately_from_http_errors(self):
        stats = gatling_console.parse(COLLAPSED)
        self.assertAlmostEqual(stats["ko_percent"], 95.76)
        self.assertEqual(stats["unreachable"], 1336886)

    def test_no_summary(self):
        self.assertIsNone(gatling_console.parse("Gatling started\n"))


class BaselineEvaluationTests(unittest.TestCase):

    def setUp(self):
        with open(_BASELINE_PATH, encoding="utf-8") as handle:
            self.baseline = json.load(handle)

    def _status(self, rows, metric):
        return next(status for label, _, _, status in rows if label == metric)

    def test_healthy_run_has_no_regressions(self):
        stats = gatling_console.parse(HEALTHY_POSTGRESQL)
        rows, regressions = ceb.evaluate(stats, self.baseline, "postgresql")
        self.assertEqual(regressions, [])
        self.assertEqual(self._status(rows, "Throughput (rps)"), "ok")

    def test_throughput_regression_is_detected_downward(self):
        stats = dict(gatling_console.parse(HEALTHY_POSTGRESQL))
        # floor is 460 * 0.7 = 322
        stats["throughput_ok"] = 300.0
        _, regressions = ceb.evaluate(stats, self.baseline, "postgresql")
        self.assertIn("Throughput", regressions)

    def test_throughput_above_baseline_is_never_a_regression(self):
        stats = dict(gatling_console.parse(HEALTHY_POSTGRESQL))
        stats["throughput_ok"] = 5000.0
        _, regressions = ceb.evaluate(stats, self.baseline, "postgresql")
        self.assertEqual(regressions, [])

    def test_latency_regression_is_detected_upward(self):
        stats = dict(gatling_console.parse(HEALTHY_POSTGRESQL))
        # ceiling is 105 * 1.25 = 131.25
        stats["mean_ms_ok"] = 200.0
        _, regressions = ceb.evaluate(stats, self.baseline, "postgresql")
        self.assertIn("Mean response time (ms)", regressions)

    def test_unknown_storage_returns_no_rows(self):
        stats = gatling_console.parse(HEALTHY_POSTGRESQL)
        rows, regressions = ceb.evaluate(stats, self.baseline, "cassandra")
        self.assertIsNone(rows)
        self.assertIsNone(regressions)

    def test_missing_metric_is_reported_unavailable_not_regression(self):
        stats = dict(gatling_console.parse(HEALTHY_POSTGRESQL))
        stats["p99_ms_ok"] = None
        rows, regressions = ceb.evaluate(stats, self.baseline, "postgresql")
        self.assertEqual(self._status(rows, "p99 response time (ms)"), "unavailable")
        self.assertNotIn("p99 response time (ms)", regressions)


class BaselineToleranceRegressionTests(unittest.TestCase):
    """The baseline must not fire on ordinary runner variance.

    These are the twelve real observations (six consecutive green main runs, both storage
    variants) the baseline values were derived from. If a future edit to
    external-baseline.json would have alerted on any of them, it is too tight and this
    fails - the check is meant to catch a step change, not runner noise.
    """

    OBSERVED = {
        "postgresql": [
            (462.89, 101), (468.85, 101), (350.98, 104),
            (483.44, 98), (459.04, 103), (467.91, 101),
        ],
        "kafkasql": [
            (1684.62, 28), (1700.47, 27), (1570.42, 30),
            (1822.17, 26), (1708.14, 28), (1370.70, 34),
        ],
    }

    def test_no_recorded_green_run_would_alert(self):
        with open(_BASELINE_PATH, encoding="utf-8") as handle:
            baseline = json.load(handle)
        for storage, runs in self.OBSERVED.items():
            for throughput, mean_ms in runs:
                stats = {
                    "throughput_ok": throughput,
                    "mean_ms_ok": mean_ms,
                    "p95_ms_ok": None,
                    "p99_ms_ok": None,
                }
                _, regressions = ceb.evaluate(stats, baseline, storage)
                self.assertEqual(
                    regressions, [],
                    f"{storage} run ({throughput} rps, {mean_ms} ms) would alert",
                )


if __name__ == "__main__":
    unittest.main()
