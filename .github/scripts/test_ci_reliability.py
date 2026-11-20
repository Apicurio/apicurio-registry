"""Regression checks for CI recovery paths; no network or container daemon required."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class CiReliabilityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.work = Path(self.temporary.name)
        self.bin = self.work / "bin"
        self.bin.mkdir()
        self.env = dict(os.environ, HOME=str(self.work), PATH=f"{self.bin}:{os.environ['PATH']}")

    def command(self, name, source):
        path = self.bin / name
        path.write_text(source)
        path.chmod(0o755)

    def run_command(self, args):
        return subprocess.run(args, cwd=self.work, env=self.env, text=True,
                              capture_output=True, timeout=30)

    def test_push_retries_and_preserves_failure(self):
        self.command("docker", """#!/usr/bin/env python3
import os
from pathlib import Path
path = Path(os.environ['HOME']) / 'calls'
count = int(path.read_text()) + 1 if path.exists() else 1
path.write_text(str(count))
raise SystemExit(0 if count >= int(os.environ['SUCCEED_ON']) else 1)
""")
        self.command("sleep", "#!/bin/sh\nexit 0\n")
        for target in ("image-push", "bundle-image-push", "catalog-image-push", "image-buildx-push"):
            for success_on, expected_calls, expected_success in ((1, 1, True), (2, 2, True), (99, 3, False)):
                with self.subTest(target=target, success_on=success_on):
                    (self.work / "calls").unlink(missing_ok=True)
                    self.env['SUCCEED_ON'] = str(success_on)
                    result = self.run_command(["make", "-f", str(ROOT / "operator/Makefile"), target,
                                               "VERSION=3.3.4-SNAPSHOT", "IMAGE=example/operator:test",
                                               "BUNDLE_IMAGE=example/bundle:test", "CATALOG_IMAGE=example/catalog:test"])
                    self.assertEqual(result.returncode == 0, expected_success, result.stderr)
                    self.assertEqual(int((self.work / "calls").read_text()), expected_calls)

    def test_collects_current_module_reports_without_logs(self):
        reports = self.work / "integration-tests/target/failsafe-reports"
        reports.mkdir(parents=True)
        (reports / "TEST-example.xml").write_text('<testsuite failures="1"/>')
        result = self.run_command(["bash", str(ROOT / ".github/scripts/collect_logs.sh")])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.work / "artifacts/failsafe-reports/TEST-example.xml").read_text(),
                         '<testsuite failures="1"/>')

    def test_collect_logs_before_reports_exist(self):
        result = self.run_command(["bash", str(ROOT / ".github/scripts/collect_logs.sh")])
        self.assertEqual(result.returncode, 0, result.stderr)

    def prepare_downloads(self):
        self.command("curl", """#!/usr/bin/env python3
import hashlib, os, sys
from pathlib import Path
args = sys.argv[1:]
assert '--retry-all-errors' in args and '--http1.1' in args and '--max-time' in args
url = next(arg for arg in args if arg.startswith('https://'))
output = Path(args[args.index('--output') + 1])
payload = b'#!/bin/sh\\nexit 0\\n'
if url.endswith('.sha256'):
    output.write_text(hashlib.sha256(payload).hexdigest())
else:
    with (Path(os.environ['HOME']) / 'downloads').open('a') as log:
        log.write(url + '\\n')
    output.write_bytes(b'corrupt' if os.environ.get('CORRUPT') else payload)
""")
        # macOS does not ship sha256sum; emulate its check interface for the script tests.
        self.command("sha256sum", """#!/usr/bin/env python3
import hashlib, sys
from pathlib import Path
checksum, path = sys.stdin.read().strip().split('  ', 1)
raise SystemExit(0 if hashlib.sha256(Path(path).read_bytes()).hexdigest() == checksum else 1)
""")

    def test_downloads_verified_binaries_and_reuses_cache(self):
        self.prepare_downloads()
        args = ["bash", str(ROOT / ".github/scripts/cache-kubernetes-binaries.sh"), "v1.33.3"]
        for _ in range(2):
            result = self.run_command(args)
            self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len((self.work / 'downloads').read_text().splitlines()), 3)
        for binary in ('kubelet', 'kubeadm', 'kubectl'):
            path = self.work / '.minikube/cache/linux/amd64/v1.33.3' / binary
            self.assertEqual(path.read_bytes(), b'#!/bin/sh\nexit 0\n')
            self.assertTrue(os.access(path, os.X_OK))

    def test_corrupt_download_never_enters_cache(self):
        self.prepare_downloads()
        self.env['CORRUPT'] = 'true'
        result = self.run_command(["bash", str(ROOT / ".github/scripts/cache-kubernetes-binaries.sh"), "v1.33.3"])
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list((self.work / '.minikube/cache/linux/amd64/v1.33.3').iterdir()), [])


if __name__ == '__main__':
    unittest.main()
