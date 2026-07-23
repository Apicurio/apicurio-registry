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

import os
import xml.etree.ElementTree as ET
import json
import argparse
import glob
import sys

def parse_args():
    parser = argparse.ArgumentParser(description="Parse Surefire/Failsafe XML reports for flaky tests.")
    parser.add_argument("--reports-dir", default=".", help="Root directory to search for reports")
    parser.add_argument("--output-file", required=True, help="Output JSON file path")
    return parser.parse_args()

def main():
    args = parse_args()
    flaky_tests = []

    # Find all TEST-*.xml files recursively
    search_pattern = os.path.join(args.reports_dir, "**/TEST-*.xml")
    xml_files = glob.glob(search_pattern, recursive=True)

    for xml_file in xml_files:
        try:
            tree = ET.parse(xml_file)
            for elem in tree.iter():
                tag_local = elem.tag.split("}")[-1]
                if tag_local == "testcase":
                    testcase = elem
                    classname = testcase.attrib.get("classname", "")
                    name = testcase.attrib.get("name", "UnknownTest")
                    
                    flaky_failures = []
                    flaky_errors = []
                    for child in testcase:
                        child_tag = child.tag.split("}")[-1]
                        if child_tag == "flakyFailure":
                            flaky_failures.append(child)
                        elif child_tag == "flakyError":
                            flaky_errors.append(child)
                            
                    if flaky_failures or flaky_errors:
                        failures_list = []
                        for f in flaky_failures + flaky_errors:
                            msg = f.attrib.get("message", "No message")
                            err_type = f.attrib.get("type", "UnknownType")
                            failures_list.append({
                                "type": f.tag.split("}")[-1],
                                "message": msg,
                                "error_type": err_type
                            })
                        
                        flaky_tests.append({
                            "class": classname,
                            "test": name,
                            "retries": len(failures_list),
                            "details": failures_list
                        })
        except Exception as e:
            # Log the error to stderr but continue parsing other files
            print(f"Error parsing XML report {xml_file}: {e}", file=sys.stderr)

    # Write output JSON
    with open(args.output_file, "w") as f:
        json.dump(flaky_tests, f, indent=2)

if __name__ == "__main__":
    main()
