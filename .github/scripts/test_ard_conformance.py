"""Tests for the ARD conformance runner and its CI wiring; no network or containers required."""
import importlib.util
import json
import os
import re
import tempfile
import threading
import unittest
from contextlib import redirect_stdout
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import StringIO
from pathlib import Path
from unittest import mock
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parents[2]
SEED_DIR = ROOT / ".github/ard-conformance/seed"
WORKFLOWS = ROOT / ".github/workflows"

spec = importlib.util.spec_from_file_location("ard_conformance", ROOT / ".github/scripts/ard_conformance.py")
ard_conformance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ard_conformance)

# Stands in for ard-spec's conformance-test: logs its arguments, exits with the configured code per mode.
FAKE_CLI = """import json, os, sys
from pathlib import Path
log = Path(os.environ['FAKE_CLI_LOG'])
log.write_text(log.read_text() + json.dumps(sys.argv[1:]) + '\\n' if log.exists() else json.dumps(sys.argv[1:]) + '\\n')
print('\\033[32mfake output for ' + sys.argv[1] + '\\033[0m')
if os.environ.get('FAKE_SKIP_SCHEMA') == sys.argv[1]:
    print("Python 'jsonschema' package not installed. Skipping strict JSON Schema check.")
sys.exit(int(os.environ.get('FAKE_FAIL_' + sys.argv[1].upper(), '0')))
"""


class FakeRegistry(BaseHTTPRequestHandler):
    created = []
    published_names = None  # None: publish everything created
    reject_post = False

    def log_message(self, *args):
        pass

    def reply(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        if self.path == "/apis/registry/v3/system/info":
            self.reply(200, {"name": "fake"})
        elif self.path == "/.well-known/ard.json":
            names = FakeRegistry.published_names
            if names is None:
                names = [json.loads(c["firstVersion"]["content"]["content"]).get("title")
                         or json.loads(c["firstVersion"]["content"]["content"])["name"]
                         for _, c in FakeRegistry.created]
            self.reply(200, {"entries": [{"displayName": n} for n in names]})
        else:
            self.reply(404, {})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if FakeRegistry.reject_post:
            self.reply(400, {"detail": "invalid artifact"})
            return
        FakeRegistry.created.append((self.path, body))
        self.reply(200, {"artifact": {"artifactId": body["artifactId"]}})


class ArdConformanceRunnerTest(unittest.TestCase):
    def setUp(self):
        FakeRegistry.created = []
        FakeRegistry.published_names = None
        FakeRegistry.reject_post = False
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeRegistry)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}"

        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.work = Path(temporary.name)
        cli = self.work / "ard-spec/conformance/bin/conformance-test"
        cli.parent.mkdir(parents=True)
        cli.write_text(FAKE_CLI)
        self.log = self.work / "cli-calls"
        self.report = self.work / "report.md"
        # Isolated environment, restored after each test; tests set FAKE_FAIL_<MODE> within it.
        env = {k: v for k, v in os.environ.items()
               if k not in ("FAKE_FAIL_MANIFEST", "FAKE_FAIL_REGISTRY", "FAKE_SKIP_SCHEMA", "GITHUB_STEP_SUMMARY")}
        env["FAKE_CLI_LOG"] = str(self.log)
        patcher = mock.patch.dict(os.environ, env, clear=True)
        patcher.start()
        self.addCleanup(patcher.stop)

    def run_main(self):
        with redirect_stdout(StringIO()) as output:
            code = ard_conformance.main(["--registry-url", self.url + "/", "--ard-spec-dir", str(self.work / "ard-spec"),
                                         "--seed-dir", str(SEED_DIR), "--ready-timeout", "5",
                                         "--report", str(self.report)])
        return code, output.getvalue()

    def cli_calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_seeds_every_artifact_then_runs_both_modes(self):
        code, output = self.run_main()
        self.assertEqual(code, 0, output)
        index = json.loads((SEED_DIR / "seed.json").read_text())
        self.assertEqual([body["artifactId"] for _, body in FakeRegistry.created],
                         [a["artifactId"] for a in index["artifacts"]])
        for path, body in FakeRegistry.created:
            parsed = urlparse(path)
            self.assertEqual(parsed.path, "/apis/registry/v3/groups/ard-conformance/artifacts")
            self.assertEqual(parse_qs(parsed.query), {"ifExists": ["FIND_OR_CREATE_VERSION"]})
            artifact = next(a for a in index["artifacts"] if a["artifactId"] == body["artifactId"])
            self.assertEqual(body["artifactType"], artifact["artifactType"])
            self.assertEqual(body["firstVersion"]["content"]["content"], (SEED_DIR / artifact["file"]).read_text())
        self.assertEqual(self.cli_calls(), [["manifest", f"{self.url}/.well-known/ard.json"],
                                            ["registry", f"{self.url}/.well-known/ard"]])
        report = self.report.read_text()
        self.assertIn("`manifest` mode: PASS", report)
        self.assertIn("`registry` mode: PASS", report)
        self.assertIn("**Overall: PASS**", report)
        self.assertIn("fake output for registry", report)
        self.assertNotIn("\x1b", report)

    def test_registry_mode_failure_fails_run(self):
        os.environ["FAKE_FAIL_REGISTRY"] = "1"
        code, _ = self.run_main()
        self.assertEqual(code, 1)
        self.assertEqual([call[0] for call in self.cli_calls()], ["manifest", "registry"])
        report = self.report.read_text()
        self.assertIn("`manifest` mode: PASS", report)
        self.assertIn("`registry` mode: FAIL", report)
        self.assertIn("**Overall: FAIL**", report)

    def test_manifest_mode_failure_still_runs_registry_mode_and_fails(self):
        os.environ["FAKE_FAIL_MANIFEST"] = "1"
        code, _ = self.run_main()
        self.assertEqual(code, 1)
        self.assertEqual([call[0] for call in self.cli_calls()], ["manifest", "registry"])
        self.assertIn("`manifest` mode: FAIL", self.report.read_text())

    def test_skipped_schema_validation_fails_despite_cli_exit_zero(self):
        os.environ["FAKE_SKIP_SCHEMA"] = "manifest"
        code, output = self.run_main()
        self.assertEqual(code, 1)
        self.assertEqual([call[0] for call in self.cli_calls()], ["manifest", "registry"])
        self.assertIn("conformance-test manifest skipped JSON Schema validation", output)
        report = self.report.read_text()
        self.assertIn("`manifest` mode: FAIL", report)
        self.assertIn("`registry` mode: PASS", report)
        self.assertIn("**Overall: FAIL**", report)

    def test_unpublished_seed_fails_before_conformance_can_pass_vacuously(self):
        FakeRegistry.published_names = ["Sentiment Analysis Agent"]
        code, output = self.run_main()
        self.assertEqual(code, 1)
        self.assertEqual(self.cli_calls(), [])
        self.assertIn("missing from /.well-known/ard.json", output)
        self.assertIn("Catalog Lookup", output)
        self.assertIn("**Overall: FAIL**", self.report.read_text())

    def test_rejected_seed_fails_with_registry_detail(self):
        FakeRegistry.reject_post = True
        code, output = self.run_main()
        self.assertEqual(code, 1)
        self.assertEqual(self.cli_calls(), [])
        self.assertIn("seeding sentiment-agent failed: HTTP 400", output)
        self.assertIn("invalid artifact", output)

    def test_missing_cli_fails_without_seeding(self):
        (self.work / "ard-spec/conformance/bin/conformance-test").unlink()
        code, output = self.run_main()
        self.assertEqual(code, 1)
        self.assertEqual(FakeRegistry.created, [])
        self.assertIn("conformance CLI not found", output)


class ArdConformanceWiringTest(unittest.TestCase):
    """The job must actually run when ARD code changes and must block merges when it fails."""

    def setUp(self):
        self.decide = (WORKFLOWS / "verify-decide.yaml").read_text()
        self.verify = (WORKFLOWS / "verify.yaml").read_text()
        self.workflow = (WORKFLOWS / "verify-ard-conformance.yaml").read_text()

    def test_gate_aggregates_conformance_job(self):
        gate_needs = re.search(r"\n  gate:\n.*?needs: \[(.*?)\]", self.verify, re.S).group(1)
        self.assertIn("ard-conformance", re.split(r"[\s,]+", gate_needs))
        job = re.search(r"\n  ard-conformance:\n(.*?)\n\n", self.verify, re.S).group(1)
        self.assertIn("needs: [decide, build-java]", job)
        self.assertIn("if: needs.decide.outputs.run-ard-conformance == 'true'", job)
        self.assertIn("uses: ./.github/workflows/verify-ard-conformance.yaml", job)
        self.assertIn("image-tag: ${{ github.sha }}", job)

    def test_decide_declares_computes_and_exports_output(self):
        self.assertIn("run-ard-conformance:\n        value: ${{ jobs.decide.outputs.run-ard-conformance }}", self.decide)
        self.assertIn("run-ard-conformance: ${{ steps.decide.outputs.run-ard-conformance }}", self.decide)
        self.assertIn("ARD='${{ steps.filter.outputs.ard }}'", self.decide)
        self.assertIn('decide run-ard-conformance "$run_tests" "$ARD"', self.decide)
        # The job needs build-java's image, so an ARD-only change must also trigger the build.
        run_build = re.search(r'decide run-build\s+(.*)', self.decide).group(1)
        self.assertIn('"$ARD"', run_build)
        draft_skip = re.search(r"for out in (run-build .*?); do", self.decide).group(1)
        self.assertIn("run-ard-conformance", draft_skip.split())

    def test_every_ard_path_filter_matches_tracked_files(self):
        block = re.search(r"\n            ard:\n((?:              (?:- '.*'|#.*)\n)+)", self.decide).group(1)
        patterns = re.findall(r"- '(.*)'", block)
        self.assertGreaterEqual(len(patterns), 7)
        # The REST contract for the ARD endpoints lives here, not under app/.
        self.assertIn("common/src/main/resources/META-INF/openapi.json", patterns)
        for pattern in patterns:
            with self.subTest(pattern=pattern):
                if pattern.endswith("/**"):
                    matches = [p for p in (ROOT / pattern[:-3]).rglob("*") if p.is_file()]
                else:
                    matches = [ROOT / pattern] if (ROOT / pattern).is_file() else []
                self.assertTrue(matches, f"{pattern} matches nothing; ARD changes would no longer trigger the job")

    def test_conformance_cli_is_pinned_to_a_commit(self):
        ref = re.search(r"ARD_SPEC_REF: (\S+)", self.workflow).group(1)
        self.assertRegex(ref, r"^[0-9a-f]{40}$")
        self.assertIn("repository: ards-project/ard-spec\n          ref: ${{ env.ARD_SPEC_REF }}", self.workflow)

    def test_schema_validator_is_pinned_and_used(self):
        version = re.search(r"JSONSCHEMA_VERSION: (\S+)", self.workflow).group(1)
        self.assertRegex(version, r"^\d+\.\d+\.\d+$")
        self.assertIn('pip" install --quiet "jsonschema==${JSONSCHEMA_VERSION}"', self.workflow)
        self.assertIn('"$RUNNER_TEMP/ard-venv/bin/python" .github/scripts/ard_conformance.py', self.workflow)

    def test_runs_on_schedule_against_main_snapshot(self):
        self.assertRegex(self.workflow, r"\n  schedule:\n    - cron: '[^']+'")
        self.assertIn("'quay.io/apicurio/apicurio-registry:latest-snapshot'", self.workflow)
        for flag in ("APICURIO_FEATURES_EXPERIMENTAL_ENABLED=true", "APICURIO_AI_CATALOG_ENABLED=true",
                     "APICURIO_ARD_ENABLED=true"):
            self.assertIn(flag, self.workflow)


class SeedContentTest(unittest.TestCase):
    def test_index_matches_seed_files(self):
        index = json.loads((SEED_DIR / "seed.json").read_text())
        files = {a["file"] for a in index["artifacts"]}
        self.assertEqual(files, {p.name for p in SEED_DIR.glob("*.json")} - {"seed.json"})
        self.assertEqual(sorted(a["artifactType"] for a in index["artifacts"]), ["AGENT_CARD"] * 4 + ["MCP_TOOL"])
        for artifact in index["artifacts"]:
            content = json.loads((SEED_DIR / artifact["file"]).read_text())
            # The published-content check compares against these names.
            self.assertEqual(content.get("title", content["name"]), artifact["name"])
            if artifact["artifactType"] == "AGENT_CARD":
                self.assertEqual(len(content["skills"]), 4)
                self.assertTrue(all(len(skill["examples"]) == 1 for skill in content["skills"]))


if __name__ == "__main__":
    unittest.main()
