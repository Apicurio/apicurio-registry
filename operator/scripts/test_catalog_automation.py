import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


SCRIPTS = Path(__file__).resolve().parent
PLACEHOLDER = "${PLACEHOLDER_PACKAGE}"


class CatalogChannelsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.catalog = Path(self.temp.name) / "catalog.yaml"
        self.catalog.write_text(json.dumps({"entries": [
            {"schema": "olm.channel", "name": channel, "entries": [
                {"name": PLACEHOLDER, "replaces": "operator.v3.3.3"},
                {"name": "operator.v3.3.3"},
            ]} for channel in ("3.x", "3.3.x")
        ]}))

    def run_update(self, *args):
        subprocess.run(["bash", str(SCRIPTS / "catalog-channels.sh"),
                        str(self.catalog), *args], check=True)
        result = subprocess.check_output([os.environ.get("YQ", "yq"),
                                          "-o=json", ".", str(self.catalog)])
        return json.loads(result)["entries"]

    def channels(self, entries):
        return {e["name"]: e["entries"] for e in entries if e["schema"] == "olm.channel"}

    def test_minor_bump_moves_only_development_placeholder(self):
        channels = self.channels(self.run_update("3.4.x"))
        self.assertEqual(channels["3.3.x"], [{"name": "operator.v3.3.3"}])
        self.assertEqual(channels["3.4.x"], [
            {"name": PLACEHOLDER, "replaces": "operator.v3.3.3"}])
        self.assertEqual(channels["3.x"][0],
                         {"name": PLACEHOLDER, "replaces": "operator.v3.3.3"})
        first = self.catalog.read_text()
        self.run_update("3.4.x")
        self.assertEqual(self.catalog.read_text(), first)

    def test_release_after_minor_bump_is_idempotent(self):
        self.run_update("3.4.x")
        args = ("3.4.x", "operator.v3.4.0", "3.4.x", "quay.io/example/bundle:3.4.0")
        entries = self.run_update(*args)
        channels = self.channels(entries)
        self.assertEqual(channels["3.x"][1],
                         {"name": "operator.v3.4.0", "replaces": "operator.v3.3.3"})
        self.assertEqual(channels["3.4.x"], [
            {"name": PLACEHOLDER, "replaces": "operator.v3.4.0"},
            {"name": "operator.v3.4.0", "replaces": "operator.v3.3.3"},
        ])
        first = self.catalog.read_text()
        self.run_update(*args)
        self.assertEqual(self.catalog.read_text(), first)
        self.assertEqual([e for e in entries if e["schema"] == "olm.bundle"],
                         [{"schema": "olm.bundle", "image": args[-1]}])

    def test_first_minor_release_keeps_edge_when_rolling_already_updated(self):
        data = json.loads(self.catalog.read_text())
        data["entries"][0]["entries"].insert(1, {
            "name": "operator.v3.4.0", "replaces": "operator.v3.3.3"})
        self.catalog.write_text(json.dumps(data))
        channels = self.channels(self.run_update(
            "3.4.x", "operator.v3.4.0", "3.4.x", "quay.io/example/bundle:3.4.0"))
        self.assertEqual(channels["3.4.x"][1], {
            "name": "operator.v3.4.0", "replaces": "operator.v3.3.3"})

    def test_skipped_development_minor_does_not_leave_empty_channel(self):
        self.run_update("3.4.x")
        channels = self.channels(self.run_update("3.5.x"))
        self.assertNotIn("3.4.x", channels)
        self.assertEqual(channels["3.5.x"], [
            {"name": PLACEHOLDER, "replaces": "operator.v3.3.3"}])

    def test_release_and_next_snapshot_have_different_minors(self):
        channels = self.channels(self.run_update(
            "3.4.x", "operator.v3.3.4", "3.3.x", "quay.io/example/bundle:3.3.4"))
        self.assertEqual(channels["3.3.x"][0],
                         {"name": "operator.v3.3.4", "replaces": "operator.v3.3.3"})
        self.assertEqual(channels["3.4.x"], [
            {"name": PLACEHOLDER, "replaces": "operator.v3.3.4"}])

    def test_maintenance_build_keeps_released_minor_history(self):
        channels = self.channels(self.run_update("3.3.x"))
        self.assertEqual(channels["3.3.x"], [
            {"name": PLACEHOLDER, "replaces": "operator.v3.3.3"},
            {"name": "operator.v3.3.3"},
        ])

    def test_repository_template_preserves_all_releases_on_minor_bump(self):
        template = SCRIPTS.parent / "olm-tests/src/test/deploy/catalog/catalog.template.yaml"
        self.catalog.write_text(template.read_text())
        before = json.loads(subprocess.check_output([
            os.environ.get("YQ", "yq"), "-o=json", ".", str(self.catalog)]))["entries"]
        after = self.run_update("3.4.x")
        channels = self.channels(after)
        self.assertEqual(channels["3.4.x"], [
            {"name": PLACEHOLDER, "replaces": "apicurio-registry-3.v3.3.3"}])
        for name, entries in self.channels(before).items():
            self.assertEqual([e for e in channels[name] if e["name"] != PLACEHOLDER],
                             [e for e in entries if e["name"] != PLACEHOLDER])
        self.assertEqual([e for e in before if e["schema"] == "olm.bundle"],
                         [e for e in after if e["schema"] == "olm.bundle"])


class OpenShiftChannelTest(unittest.TestCase):
    def test_workflow_preserves_cross_minor_upgrade_edge(self):
        # Exercise the actual workflow loop without its network/git/PR operations.
        workflow = (SCRIPTS.parents[1] / ".github/workflows/release-operator.yaml").read_text()
        start = workflow.index("          for tpl in catalog-templates/v4.*.yaml; do")
        end = workflow.index("          make catalogs", start)
        loop = textwrap.dedent(workflow[start:end])
        with tempfile.TemporaryDirectory() as directory:
            templates = Path(directory) / "catalog-templates"
            templates.mkdir()
            for initial_minor in (None, []):
                with self.subTest(initial_minor=initial_minor):
                    entries = [
                        {"schema": "olm.channel", "name": "3.x", "entries": [{"name": "operator.v3.3.3"}]},
                        {"schema": "olm.channel", "name": "3.3.x", "entries": [{"name": "operator.v3.3.3"}]},
                    ]
                    if initial_minor is not None:
                        entries.append({"schema": "olm.channel", "name": "3.4.x", "entries": initial_minor})
                    template = templates / "v4.20.yaml"
                    template.write_text(json.dumps({"entries": entries}))
                    env = {**os.environ, "YQ": os.environ.get("YQ", "yq"),
                           "PKG_NAME": "operator", "CSV_NAME": "operator.v3.4.0",
                           "PREVIOUS_CSV": "operator.v3.3.3", "CHANNELS": "3.x 3.4.x",
                           "BUNDLE_IMG": "quay.io/community-operator-pipeline-prod/operator@sha256:" + "a" * 64}
                    subprocess.run(["bash", "-euo", "pipefail", "-c", loop],
                                   env=env, cwd=directory, check=True, capture_output=True, timeout=30)
                    data = json.loads(subprocess.check_output([env["YQ"], "-o=json", ".", str(template)]))
                    channels = {e["name"]: e["entries"] for e in data["entries"] if e["schema"] == "olm.channel"}
                    self.assertEqual(channels["3.4.x"], [
                        {"name": "operator.v3.4.0", "replaces": "operator.v3.3.3"}])
                    self.assertEqual(channels["3.3.x"], [{"name": "operator.v3.3.3"}])
                    self.assertEqual(channels["3.x"][0], channels["3.4.x"][0])
                    first = template.read_text()
                    subprocess.run(["bash", "-euo", "pipefail", "-c", loop],
                                   env=env, cwd=directory, check=True, capture_output=True, timeout=30)
                    self.assertEqual(template.read_text(), first)


class PublishedBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.env = {**os.environ, "PATH": f"{self.directory}:{os.environ['PATH']}"}
        self.command("sleep", "exit 0")

    def command(self, name, body):
        path = self.directory / name
        path.write_text("#!/bin/bash\n" + body + "\n")
        path.chmod(0o755)

    def run_resolve(self):
        return subprocess.run(["bash", str(SCRIPTS / "published-bundle.sh"), "3.4.0"],
                              env=self.env, capture_output=True, text=True)

    def test_resolves_only_exact_version_to_digest(self):
        digest = "sha256:" + "a" * 64
        response = json.dumps({"tags": [
            {"name": "3.4.0-old", "manifest_digest": "sha256:" + "b" * 64},
            {"name": "3.4.0", "manifest_digest": digest},
        ]})
        self.command("curl", f"printf '%s' '{response}'")
        result = self.run_resolve()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(),
                         f"quay.io/community-operator-pipeline-prod/apicurio-registry-3@{digest}")

    def test_missing_or_invalid_digest_never_falls_back(self):
        for tags in ([], [{"name": "3.4.0", "manifest_digest": "invalid"}]):
            with self.subTest(tags=tags):
                self.command("curl", "printf '%s' '" + json.dumps({"tags": tags}) + "'")
                result = self.run_resolve()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")
                self.assertIn("attempt 30/30", result.stderr)

    def test_transient_failure_is_retried(self):
        marker = self.directory / "attempt"
        response = json.dumps({"tags": [{"name": "3.4.0", "manifest_digest": "sha256:" + "c" * 64}]})
        self.command("curl", f'if [ ! -f "{marker}" ]; then touch "{marker}"; exit 22; fi\n'
                     + f"printf '%s' '{response}'")
        result = self.run_resolve()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("attempt 1/30", result.stderr)


if __name__ == "__main__":
    unittest.main()
