"""Wiring checks of the example CI suite in the required Verify pipeline; no network, container daemon or browser required.

They read the real workflow files and scripts and check what the acceptance of the pipeline stands on: that the example's own suite is one
explicit step after both unchanged demo runs, that nothing in it can mask a failure, that a failure reaches the Verify gate, that a change
of the canonical dashboard selects the Examples job (the image it needs and the job itself), that artifacts leave the runner only through
the suite's collector, and that privileged installation stays in the workflow and out of the example's scripts.
"""
import json
import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
EXAMPLE = ROOT / "examples" / "usage-telemetry-otel"
DASHBOARD = "distro/metrics/usage/grafana/schema-usage.json"


def code_lines(path):
    """The lines of a file without comment-only lines, so a comment never satisfies or breaks a check."""
    return [line for line in path.read_text().splitlines() if not line.lstrip().startswith("#")]


def job_steps(path, job):
    """The steps of one job as (name, text), by indentation: a job at two spaces, its steps as list items at six."""
    lines = code_lines(path)
    start = next(i for i, line in enumerate(lines) if line == "  %s:" % job)
    end = next((i for i in range(start + 1, len(lines)) if re.match(r"^  [A-Za-z0-9_-]+:", lines[i])), len(lines))
    steps = []
    for line in lines[start:end]:
        if re.match(r"^      - ", line):
            steps.append([line, []])
        elif steps and line.startswith("      "):
            steps[-1][1].append(line)
    result = []
    for first, rest in steps:
        text = "\n".join([first] + rest)
        name = re.search(r"name:\s*(.+)", text)
        result.append((name.group(1).strip() if name else first.strip(), text))
    return result


def step(steps, name):
    found = [text for step_name, text in steps if step_name == name]
    assert len(found) == 1, "expected exactly one step named %r, found %d" % (name, len(found))
    return found[0]


def index(steps, name):
    return [step_name for step_name, _ in steps].index(name)


class ExampleSuiteWiringTest(unittest.TestCase):
    # Short failure messages: the texts under test are whole files, which a default message would print
    def assertIn(self, needle, haystack, msg=None):
        if needle not in haystack:
            self.fail("%s: %r is missing" % (msg or "expected text", needle))

    def assertNotIn(self, needle, haystack, msg=None):
        if needle in haystack:
            self.fail("%s: %r is present" % (msg or "unexpected text", needle))

    def assertRegex(self, text, pattern, msg=None):
        if not re.search(pattern, text):
            self.fail("%s: nothing matches %r" % (msg or "expected text", pattern))

    def assertNotRegex(self, text, pattern, msg=None):
        found = re.search(pattern, text)
        if found:
            self.fail("%s: %r matches %r" % (msg or "unexpected text", found.group(0), pattern))

    def setUp(self):
        self.examples = job_steps(WORKFLOWS / "verify-examples.yaml", "run")

    # --- the steps of the example job -------------------------------------------------------------------------------------------
    def test_the_suite_is_one_explicit_step_after_both_unchanged_demo_runs(self):
        names = [name for name, _ in self.examples]
        demo, again, install = "Run demo.sh", "Run demo.sh again (must be safe to repeat)", "Install the browser's system packages"
        suite, upload = "Run the example's CI suite", "Upload the example's CI artifacts"
        self.assertEqual(1, names.count(suite), "the suite is one step, not one per phase or per demo run")
        self.assertLess(index(self.examples, demo), index(self.examples, again))
        self.assertLess(index(self.examples, again), index(self.examples, install))
        self.assertLess(index(self.examples, install), index(self.examples, suite))
        self.assertLess(index(self.examples, suite), index(self.examples, upload))
        self.assertLess(index(self.examples, upload), index(self.examples, "Stack logs"))
        self.assertLess(index(self.examples, "Stack logs"), index(self.examples, "Stop the stack"))
        for name in (demo, again):
            self.assertRegex(step(self.examples, name), r"run: \./demo\.sh\s*$", "the demo invocations are the existing, unchanged ones")
            self.assertNotIn("if:", step(self.examples, name), "the demos are not conditional")

    def test_nothing_masks_a_failure_of_the_suite(self):
        text = step(self.examples, "Run the example's CI suite")
        self.assertIn("if: matrix.example.suite != ''", text, "conditional on the discovered setting, not on a marker file")
        self.assertNotIn("continue-on-error", text)
        self.assertNotRegex(text, r"\|\|\s*(true|:|exit 0)", "no unconditional success after the suite")
        self.assertRegex(text, r'run: \./"\$CI_SUITE_SCRIPT"\s*$', "the suite's exit status is the step's")
        self.assertNotIn("continue-on-error", "\n".join(code_lines(WORKFLOWS / "verify-examples.yaml")), "the Examples workflow")
        verify = "\n".join(code_lines(WORKFLOWS / "verify.yaml"))
        self.assertNotIn("continue-on-error", verify, "the Verify workflow, which holds the Examples job and the gate")

    def test_the_suite_gets_this_commits_image_and_a_fresh_artifact_directory_and_docker(self):
        text = step(self.examples, "Run the example's CI suite")
        self.assertIn("CI_ARTIFACT_DIR: ${{ runner.temp }}/example-ci-${{ matrix.example.name }}", text)
        self.assertIn("CONTAINER_RUNTIME: docker", text, "the example prefers podman, which is also on the runner but does not hold this commit's image")
        jobenv = "\n".join(code_lines(WORKFLOWS / "verify-examples.yaml"))
        self.assertIn("REGISTRY_IMAGE: apicurio/apicurio-registry:${{ inputs.image-tag }}", jobenv)
        self.assertIn("-mutable.tar", jobenv)
        suite = (EXAMPLE / "tests" / "ci-suite.sh").read_text()
        self.assertIn('[ -n "${REGISTRY_IMAGE:-}" ] || die', suite, "the suite refuses to run without the image of this commit")

    def test_only_the_collectors_publish_directory_leaves_the_runner(self):
        text = step(self.examples, "Upload the example's CI artifacts")
        self.assertIn("if: always() && matrix.example.suite != ''", text, "also on failure and cancellation")
        self.assertIn("name: example-ci-${{ matrix.example.name }}", text)
        self.assertIn("path: ${{ runner.temp }}/example-ci-${{ matrix.example.name }}/publish", text)
        self.assertNotIn("work", text.replace("workspace", ""), "the full logs and work files stay on the runner")
        self.assertNotRegex(text, r"path:.*(test-results|playwright-report|node_modules|\.browsers|\.local)")
        uploads = [t for _, t in self.examples if "actions/upload-artifact@" in t]
        self.assertEqual(2, len(uploads), "the existing stack-log upload and this one")
        pins = {re.search(r"upload-artifact@(\S+)", t).group(1) for t in uploads}
        self.assertEqual(1, len(pins), "both uploads use the same pinned version")

    def test_raw_compose_logs_are_not_replayed_or_uploaded_for_an_example_with_a_suite(self):
        for name in ("Stack logs", "Upload stack logs"):
            text = step(self.examples, name)
            self.assertIn("matrix.example.suite == ''", text, name + " is for examples without a suite")
            self.assertIn("matrix.example.compose", text, name + " still covers the other examples")

    def test_the_privileged_installation_is_in_the_workflow_and_not_in_the_examples_scripts(self):
        text = step(self.examples, "Install the browser's system packages")
        self.assertIn("if: matrix.example.playwrightDir != ''", text)
        self.assertIn('npm ci --prefix "$PLAYWRIGHT_DIR"', text)
        self.assertRegex(text, r'sudo env "PATH=\$PATH" "\$PLAYWRIGHT_DIR/node_modules/\.bin/playwright" install-deps chromium')
        offenders = []
        for path in sorted(EXAMPLE.rglob("*")):
            if path.suffix not in (".sh", ".py", ".mjs", ".ts") or "node_modules" in path.parts or path.name == "ci-suite-test.sh":
                continue
            for number, line in enumerate(path.read_text().splitlines(), 1):
                if re.search(r"\bsudo\b|install-deps|--with-deps", line) and not line.lstrip().startswith(("#", "//")) and "playwright install" not in line.split("#")[0].replace("install-deps", ""):
                    offenders.append("%s:%d: %s" % (path.relative_to(ROOT), number, line.strip()))
        self.assertEqual([], offenders, "no script of the example installs system packages or uses sudo")

    # --- discovery and the example's settings -----------------------------------------------------------------------------------
    def test_discovery_hands_the_example_its_suite_and_the_workflow_reads_only_discovered_values(self):
        out = subprocess.run([str(ROOT / ".github/scripts/discover-examples.sh")], cwd=ROOT, capture_output=True, text=True, timeout=60)
        self.assertEqual(0, out.returncode, out.stderr)
        entry = next(item for item in json.loads(out.stdout) if item["name"] == "usage-telemetry-otel")
        self.assertEqual("tests/ci-suite.sh", entry["suite"])
        self.assertEqual("tests/ui", entry["playwrightDir"])
        self.assertIn("node", entry["toolchains"].split())
        self.assertGreaterEqual(entry["timeout"], 60)
        self.assertTrue(os.access(EXAMPLE / entry["suite"], os.X_OK))
        yaml = "\n".join(code_lines(WORKFLOWS / "verify-examples.yaml"))
        self.assertNotIn("run: ./tests/", yaml, "no suite command is spelled in the shared workflow")
        for template in re.findall(r"run: \|.*?(?=\n      - |\Z)", yaml, re.S):
            self.assertNotRegex(template, r"\$\{\{ matrix\.example\.(suite|playwrightDir) \}\}", "discovered values reach shell steps through env, not interpolation")

    def test_the_suite_runs_the_required_phases_in_the_required_order_and_never_in_parallel(self):
        text = "\n".join(code_lines(EXAMPLE / "tests" / "ci-suite.sh"))
        order = ["./tests/unit-test.sh", "./tests/dashboard-unit-test.sh", "./tests/dashboard-promql-test.sh", "./test-ui.sh --setup-only", "./tests/lifecycle-test.sh",
                 "./tests/dashboard-scenarios.sh --keep", "./test-ui.sh --no-install --scenario"]
        positions = [text.index(item) for item in order]
        self.assertEqual(sorted(positions), positions, "the phases are in the documented order")
        self.assertNotRegex(text, r"(^|[^&|])&\s*$", "no phase is started in the background")
        self.assertIn("real-tooling-test.sh", text)
        self.assertIn("clone-dir-test.sh", text)
        self.assertIn("--no-install", text, "the browser phases never download")

    def test_the_template_documents_the_two_new_settings_and_the_artifacts(self):
        template = (ROOT / "examples" / "TEMPLATE.md").read_text()
        for key in ("CI_SUITE", "CI_PLAYWRIGHT_DIR"):
            self.assertIn("`%s`" % key, template)
        guide = (EXAMPLE / "GUIDE.md").read_text() + (EXAMPLE / "README.md").read_text()
        self.assertIn("example-ci-usage-telemetry-otel", guide, "the artifact name is documented")

    # --- the Verify pipeline ----------------------------------------------------------------------------------------------------
    def test_the_examples_job_is_part_of_the_required_gate_and_a_failure_fails_it(self):
        verify = (WORKFLOWS / "verify.yaml").read_text()
        examples = re.search(r"\n  examples:\n(.*?)\n  operator:", verify, re.S).group(1)
        self.assertIn("if: needs.decide.outputs.run-examples == 'true'", examples)
        self.assertIn("uses: ./.github/workflows/verify-examples.yaml", examples)
        self.assertNotIn("continue-on-error", examples)
        gate = re.search(r"\n  gate:\n(.*?)\n  # =+\n  # PUBLISH", verify, re.S).group(1)
        needs = re.search(r"needs: \[(.*?)\]", gate, re.S).group(1)
        self.assertIn("examples", [item.strip() for item in needs.split(",")], "the Verify result waits for the Examples job")
        command = re.search(r"FAILED=\$\((.*)\)\n", gate).group(0).strip()

        def failed(results):
            run = subprocess.run(["bash", "-c", 'RESULTS=\'%s\'; %s; echo "$FAILED"' % (json.dumps(results), command)], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, run.returncode, run.stderr)
            return int(run.stdout.strip())

        self.assertEqual(0, failed(["success", "skipped", "success"]), "green and skipped jobs pass the gate")
        self.assertEqual(1, failed(["success", "failure", "skipped"]), "a failed Examples job fails the gate")
        self.assertEqual(1, failed(["success", "cancelled"]), "a cancelled one (a timeout) fails it too")

    def test_a_change_of_the_canonical_dashboard_selects_the_image_and_the_examples(self):
        self.assertTrue((ROOT / DASHBOARD).is_file(), "the canonical dashboard is where the example mounts it from")
        compose = (EXAMPLE / "docker-compose.yml").read_text()
        self.assertIn("distro/metrics/usage/grafana", compose, "this example mounts the canonical dashboard")
        decide = (WORKFLOWS / "verify-decide.yaml").read_text()
        block = re.search(r"filters: \|\n(.*?)\n\n      - name:", decide, re.S).group(1)
        filters = {}
        current = None
        for line in block.splitlines():
            key = re.match(r"^ {12}([a-z-]+):\s*$", line)
            pattern = re.match(r"^ {14}- '([^']+)'", line)
            if key:
                current = filters.setdefault(key.group(1), [])
            elif pattern and current is not None:
                current.append(pattern.group(1))

        def matches(pattern, path):
            return re.fullmatch(re.escape(pattern).replace(r"\*\*", ".*").replace(r"\*", "[^/]*"), path) is not None

        def flags(path):
            return {name: any(matches(pattern, path) for pattern in patterns) for name, patterns in filters.items()}

        self.assertTrue(flags(DASHBOARD)["java"], "the dashboard is under distro/**, which the Java filter covers")
        self.assertTrue(flags("examples/usage-telemetry-otel/tests/ci-suite.sh")["examples"])
        self.assertTrue(flags(".github/scripts/discover-examples.sh")["examples"])
        self.assertTrue(flags(".github/workflows/verify-examples.yaml")["ci"])
        self.assertFalse(any(flags("docs/modules/ROOT/pages/index.adoc")[name] for name in ("java", "examples", "ci")), "control: a page of the documentation selects none")

        function = re.search(r"\n( {10})decide\(\) \{\n(.*?)\n\1\}\n", decide, re.S)
        build = re.search(r"^ {10}(decide run-build .*)$", decide, re.M).group(1)
        examples = re.search(r"^ {10}(decide run-examples .*)$", decide, re.M).group(1)
        for line in (build, examples):
            self.assertIn('"$JAVA"', line, "a Java-side change (the dashboard's distro/**) is a reason for this decision")

        def decided(path, scope="true", push="false"):
            variables = {name.upper().replace("-", "_"): str(value).lower() for name, value in flags(path).items()}
            with tempfile.TemporaryDirectory() as work:
                output = Path(work) / "out"
                script = "decide() {\n%s\n}\nIS_PUSH=%s\nrun_tests=%s\n%s\n%s\n%s\n" % (
                    function.group(2), push, scope,
                    "\n".join("%s=%s" % item for item in variables.items()), build, examples)
                run = subprocess.run(["bash", "-c", script], env=dict(os.environ, GITHUB_OUTPUT=str(output), CI=variables.get("CI", "false")), capture_output=True, text=True, timeout=30)
                self.assertEqual(0, run.returncode, run.stderr)
                return dict(line.split("=", 1) for line in output.read_text().splitlines())

        self.assertEqual({"run-build": "true", "run-examples": "true"}, decided(DASHBOARD), "only the dashboard changed: the image is built and the examples run")
        self.assertEqual({"run-build": "false", "run-examples": "false"}, decided("docs/modules/ROOT/pages/index.adoc"), "control: documentation alone runs neither")
        self.assertEqual({"run-build": "false", "run-examples": "false"}, decided(DASHBOARD, scope="false"), "control: outside the lifecycle scope nothing runs")


if __name__ == "__main__":
    unittest.main()
