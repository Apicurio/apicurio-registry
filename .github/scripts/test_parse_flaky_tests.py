# Copyright 2026 Red Hat
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import unittest
import tempfile
import os
import sys
import importlib.util

# Dynamically import parse-flaky-tests.py which contains hyphens in filename
script_dir = os.path.dirname(os.path.abspath(__file__))
script_path = os.path.join(script_dir, "parse-flaky-tests.py")

spec = importlib.util.spec_from_file_location("parse_flaky_tests", script_path)
parse_flaky_tests = importlib.util.module_from_spec(spec)
sys.modules["parse_flaky_tests"] = parse_flaky_tests
spec.loader.exec_module(parse_flaky_tests)


class TestParseFlakyTests(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_parse_report_flaky_failure_and_error(self):
        xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="io.apicurio.test.FlakyTestSuite" time="1.23">
    <testcase classname="io.apicurio.test.FlakyTestSuite" name="testFlakyFeature" time="0.45">
        <flakyFailure message="Connection reset by peer" type="java.io.IOException">
            <stackTrace>java.io.IOException: Connection reset...</stackTrace>
        </flakyFailure>
        <flakyError message="Timeout waiting for condition" type="java.util.concurrent.TimeoutException">
            <stackTrace>java.util.concurrent.TimeoutException...</stackTrace>
        </flakyError>
    </testcase>
    <testcase classname="io.apicurio.test.FlakyTestSuite" name="testPassingFeature" time="0.10">
    </testcase>
</testsuite>
"""
        file_path = os.path.join(self.temp_dir.name, "TEST-FlakyTestSuite.xml")
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(xml_content)

        results = parse_flaky_tests.parse_report(file_path)

        self.assertEqual(len(results), 1)
        flaky_case = results[0]
        self.assertEqual(flaky_case["class"], "io.apicurio.test.FlakyTestSuite")
        self.assertEqual(flaky_case["test"], "testFlakyFeature")
        self.assertEqual(flaky_case["retries"], 2)

        details = flaky_case["details"]
        self.assertEqual(len(details), 2)
        self.assertEqual(details[0], {
            "type": "flakyFailure",
            "message": "Connection reset by peer",
            "error_type": "java.io.IOException"
        })
        self.assertEqual(details[1], {
            "type": "flakyError",
            "message": "Timeout waiting for condition",
            "error_type": "java.util.concurrent.TimeoutException"
        })

    def test_parse_report_no_flaky_tests(self):
        xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="io.apicurio.test.PassingTestSuite">
    <testcase classname="io.apicurio.test.PassingTestSuite" name="testSuccess" time="0.05"/>
</testsuite>
"""
        file_path = os.path.join(self.temp_dir.name, "TEST-PassingTestSuite.xml")
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(xml_content)

        results = parse_flaky_tests.parse_report(file_path)
        self.assertEqual(results, [])

    def test_parse_report_invalid_xml(self):
        file_path = os.path.join(self.temp_dir.name, "TEST-Invalid.xml")
        with open(file_path, "w", encoding="utf-8") as f:
            f.write("<invalid xml string line without closing tags")

        results = parse_flaky_tests.parse_report(file_path)
        self.assertEqual(results, [])


if __name__ == "__main__":
    unittest.main()
