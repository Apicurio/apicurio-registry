import io
import os
import sys
import unittest
from contextlib import redirect_stdout

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import importlib.util

_spec = importlib.util.spec_from_file_location(
    "check_external_validity",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-external-validity.py"),
)
cev = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cev)


COLLAPSED = """
========================================================================================================================
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      | 1,484,140 |    78,098 | 1,406,042
> mean throughput (rps)                                                              |   7,420.7 |    390.49 |  7,030.21
---- Response Time Distribution ----------------------------------------------------------------------------------------
> OK: t < 800 ms                                                                                         75,703   (5.1%)
> KO                                                                                                  1,406,042 (94.74%)
---- Errors ------------------------------------------------------------------------------------------------------------
> j.n.ConnectException: finishConnect(..) failed with error(-111): Connection refused                 1,404,879 (99.92%)
> status.find.is(200), but actually found 500                                                               686  (0.05%)
> j.i.IOException: Premature close                                                                          467  (0.03%)
========================================================================================================================
"""

HEALTHY = """
========================================================================================================================
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      |     9,739 |     9,739 |         0
---- Response Time Distribution ----------------------------------------------------------------------------------------
> OK: t < 800 ms                                                                                          9,739   (100%)
> KO                                                                                                          0     (0%)
========================================================================================================================
"""

# Target reachable, but answering with errors - slow/unhappy, not absent.
APP_ERRORS = """
========================================================================================================================
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      |     1,000 |       600 |       400
---- Response Time Distribution ----------------------------------------------------------------------------------------
> KO                                                                                                        400    (40%)
---- Errors ------------------------------------------------------------------------------------------------------------
> status.find.is(200), but actually found 500                                                               400   (100%)
========================================================================================================================
"""


class ParseTests(unittest.TestCase):

    def test_collapsed_run_counts_and_attributes_unreachable(self):
        stats = cev.parse(COLLAPSED)
        self.assertEqual(stats["total"], 1484140)
        self.assertEqual(stats["ok"], 78098)
        self.assertEqual(stats["ko"], 1406042)
        self.assertAlmostEqual(stats["ko_percent"], 94.74)
        # Connection refused + premature close, but not the HTTP 500s.
        self.assertEqual(stats["unreachable"], 1404879 + 467)

    def test_healthy_run(self):
        stats = cev.parse(HEALTHY)
        self.assertEqual(stats["total"], 9739)
        self.assertEqual(stats["ko"], 0)
        self.assertEqual(stats["ko_percent"], 0.0)
        self.assertEqual(stats["unreachable"], 0)

    def test_http_errors_are_not_counted_as_unreachable(self):
        stats = cev.parse(APP_ERRORS)
        self.assertEqual(stats["ko"], 400)
        self.assertEqual(stats["unreachable"], 0)

    def test_no_summary_returns_none(self):
        self.assertIsNone(cev.parse("Gatling started...\nno summary here\n"))


class ExitStatusTests(unittest.TestCase):

    def _run(self, text, argv_extra=()):
        path = os.path.join(self._dir, "log.txt")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
        argv = sys.argv
        sys.argv = ["check-external-validity.py", path, *argv_extra]
        buffer = io.StringIO()
        try:
            with redirect_stdout(buffer):
                code = cev.main()
        finally:
            sys.argv = argv
        return code, buffer.getvalue()

    def setUp(self):
        import tempfile
        self._tmp = tempfile.TemporaryDirectory()
        self._dir = self._tmp.name

    def tearDown(self):
        self._tmp.cleanup()

    def test_collapsed_run_fails(self):
        code, out = self._run(COLLAPSED)
        self.assertEqual(code, 1)
        self.assertIn("not a usable measurement", out)
        self.assertIn("connect-level", out)

    def test_healthy_run_passes(self):
        code, out = self._run(HEALTHY)
        self.assertEqual(code, 0)
        self.assertIn("Run is usable", out)

    def test_threshold_is_respected(self):
        # 40% failures: over the default 25, under an explicit 50.
        self.assertEqual(self._run(APP_ERRORS)[0], 1)
        self.assertEqual(self._run(APP_ERRORS, ("--max-ko-percent", "50"))[0], 0)

    def test_app_error_failure_is_not_blamed_on_unreachability(self):
        _, out = self._run(APP_ERRORS)
        self.assertNotIn("connect-level", out)

    def test_missing_summary_fails(self):
        code, out = self._run("nothing useful\n")
        self.assertEqual(code, 1)
        self.assertIn("did not complete", out)


if __name__ == "__main__":
    unittest.main()
