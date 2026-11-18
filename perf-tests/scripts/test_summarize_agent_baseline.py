import importlib.util
import os
import unittest

_HERE = os.path.dirname(os.path.abspath(__file__))


def _load_script(name):
    path = os.path.join(_HERE, name)
    spec = importlib.util.spec_from_file_location(name.replace("-", "_")[:-3], path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


sab = _load_script("summarize-agent-baseline.py")


def _block(name, count, ko, mean, p95, p99, top):
    def metric(key, value, failed="-"):
        return f'    "{key}": {{\n        "total": "{value}",\n        "ok": "{value}",\n        "ko": "{failed}"\n    }},\n'
    return (f'        name: "{name}",\n' + metric("numberOfRequests", count, ko) + metric("minResponseTime", 1)
            + metric("maxResponseTime", top) + metric("meanResponseTime", mean)
            + metric("percentiles3", p95) + metric("percentiles4", p99))


# The shape of Gatling's js/stats.js: a JS object literal, one block per request after the global one
STATS = ("var stats = {\n" + _block("All Requests", 7004, 1, 6, 15, 29, 1090) + "contents: {\n"
         + _block("ARD search by text", 2300, 0, 2, 7, 22, 102)
         + _block("MCP import (500 tools)", 4, 0, 625, 1090, 1090, 1090) + "}};\n")


class SummarizeAgentBaselineTest(unittest.TestCase):

    def test_one_row_per_request_with_count_failures_and_latencies(self):
        summary = sab.summarize(STATS, "postgresql")
        self.assertIn("### Agent registry scale baseline (postgresql)", summary)
        self.assertIn("| All Requests | 7004 | 1 | 6 | 15 | 29 | 1090 |", summary)
        self.assertIn("| ARD search by text | 2300 | 0 | 2 | 7 | 22 | 102 |", summary)
        self.assertIn("| MCP import (500 tools) | 4 | 0 | 625 | 1090 | 1090 | 1090 |", summary)

    def test_import_throughput_from_the_tool_count_and_mean_time(self):
        summary = sab.summarize(STATS, "")
        self.assertIn("MCP import: 500 tools in 625 ms on average, 800 tools/s", summary)
        self.assertIn("### Agent registry scale baseline\n", summary)

    def test_missing_values_are_shown_as_dashes(self):
        stats = STATS.replace('"total": "4",\n        "ok": "4",\n        "ko": "0"',
                              '"total": "4",\n        "ok": "4",\n        "ko": "-"')
        self.assertIn("| MCP import (500 tools) | 4 | - |", sab.summarize(stats, ""))


if __name__ == "__main__":
    unittest.main()
