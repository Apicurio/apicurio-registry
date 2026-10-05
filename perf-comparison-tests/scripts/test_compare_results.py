import importlib.util
import json
import pathlib
import tempfile
import unittest


SCRIPT_PATH = pathlib.Path(__file__).with_name("compare-results.py")
SPEC = importlib.util.spec_from_file_location("compare_results", SCRIPT_PATH)
COMPARE_RESULTS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COMPARE_RESULTS)


class CompareResultsTest(unittest.TestCase):

    def test_rps_uses_configured_measured_window(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = pathlib.Path(directory) / "run-1"
            stats_path = run_dir / "gatling" / "simulation" / "js" / "stats.js"
            stats_path.parent.mkdir(parents=True)
            (run_dir / "deployment-metadata.json").write_text(json.dumps({
                "product": "apicurio",
                "operation": "READ_ID",
                "duration": 180
            }))
            stats_path.write_text("""
var stats = {
    contents: {
        measured: {
            type: "REQUEST", name: "Measured READ_ID",
            stats: {
                "numberOfRequests": {"total": "900002", "ok": "900000", "ko": "2"},
                "meanNumberOfRequestsPerSecond": {"total": "2500.01", "ok": "2500", "ko": "0.01"},
                "meanResponseTime": {"total": "12", "ok": "10", "ko": "60000"},
                "percentiles1": {"total": "8", "ok": "8", "ko": "60000"},
                "percentiles2": {"total": "20", "ok": "20", "ko": "60000"},
                "percentiles3": {"total": "30", "ok": "30", "ko": "60000"},
                "percentiles4": {"total": "40", "ok": "40", "ko": "60000"}
            }
        }
    }
}
""")

            result = COMPARE_RESULTS.parse_run(stats_path)

            self.assertEqual(5000, result["rps"])
            self.assertEqual(2500, result["gatlingSimulationWindowRps"])
            self.assertEqual(900002, result["total"])
            self.assertEqual(900000, result["ok"])
            self.assertEqual(2, result["ko"])

    def test_rejects_invalid_measured_duration(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = pathlib.Path(directory) / "run-1"
            stats_path = run_dir / "gatling" / "simulation" / "js" / "stats.js"
            stats_path.parent.mkdir(parents=True)
            (run_dir / "deployment-metadata.json").write_text(json.dumps({"duration": 0}))
            stats_path.write_text("""
type: "REQUEST", name: "Measured READ_ID",
"numberOfRequests": {"total": 1, "ok": 1, "ko": 0}
    }
}
""")

            with self.assertRaisesRegex(ValueError, "Invalid measured duration"):
                COMPARE_RESULTS.parse_run(stats_path)


if __name__ == "__main__":
    unittest.main()
