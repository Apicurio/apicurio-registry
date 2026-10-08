#!/usr/bin/env python3
"""Seed a registry with AI Catalog/ARD content and run the official ARD conformance CLI.

Runs the CLI in manifest mode (/.well-known/ard.json) and registry mode
(/.well-known/ard). Before running it, checks that every seeded artifact is
published in the manifest, so an empty catalog cannot pass conformance
vacuously. Exits non-zero if seeding, the published-content check, or either
conformance mode fails. Uses only the Python standard library.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# The CLI always colours its output; keep colour on the console but not in the Markdown report.
ANSI_ESCAPE = re.compile(r"\x1b\[[0-9;]*m")


def request(method, url, body=None, timeout=30):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json", "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.status, response.read().decode("utf-8")


def wait_until_ready(base_url, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    last_error = None
    while time.monotonic() < deadline:
        try:
            # The REST API itself, not a health endpoint: health can live on a separate management port.
            status, _ = request("GET", f"{base_url}/apis/registry/v3/system/info", timeout=5)
            if status == 200:
                return
            last_error = f"HTTP {status}"
        except (urllib.error.URLError, OSError) as error:
            last_error = error
        time.sleep(2)
    raise RuntimeError(f"registry at {base_url} not ready after {timeout_seconds}s: {last_error}")


def seed(base_url, seed_dir):
    index = json.loads((seed_dir / "seed.json").read_text())
    group = urllib.parse.quote(index["groupId"], safe="")
    # FIND_OR_CREATE_VERSION keeps re-seeding an existing instance idempotent.
    url = f"{base_url}/apis/registry/v3/groups/{group}/artifacts?ifExists=FIND_OR_CREATE_VERSION"
    for artifact in index["artifacts"]:
        content = (seed_dir / artifact["file"]).read_text()
        body = {
            "artifactId": artifact["artifactId"],
            "artifactType": artifact["artifactType"],
            "firstVersion": {"content": {"content": content, "contentType": "application/json"}},
        }
        try:
            request("POST", url, body)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"seeding {artifact['artifactId']} failed: HTTP {error.code} "
                               f"{error.read().decode('utf-8', 'replace')}") from error
        print(f"Seeded {artifact['artifactType']} {index['groupId']}/{artifact['artifactId']}")
    return [artifact["name"] for artifact in index["artifacts"]]


def check_published(base_url, expected_names):
    _, body = request("GET", f"{base_url}/.well-known/ard.json")
    published = {entry.get("displayName") for entry in json.loads(body).get("entries", [])}
    missing = sorted(set(expected_names) - published)
    if missing:
        raise RuntimeError(f"seeded artifacts missing from /.well-known/ard.json: {missing}")
    print(f"All {len(expected_names)} seeded artifacts are published in /.well-known/ard.json")


def run_cli(cli, mode, target, report):
    print(f"\n$ conformance-test {mode} {target}", flush=True)
    result = subprocess.run([sys.executable, str(cli), mode, target], text=True, capture_output=True)
    print(result.stdout + result.stderr)
    output = ANSI_ESCAPE.sub("", result.stdout + result.stderr)
    report.append(f"### `{mode}` mode: {'PASS' if result.returncode == 0 else 'FAIL'}\n\n"
                  f"```\n$ conformance-test {mode} {target}\n{output}```\n")
    return result.returncode == 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry-url", required=True)
    parser.add_argument("--ard-spec-dir", required=True, type=Path)
    parser.add_argument("--seed-dir", required=True, type=Path)
    parser.add_argument("--ard-spec-ref", default="unknown")
    parser.add_argument("--ready-timeout", type=int, default=180)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)

    base_url = args.registry_url.rstrip("/")
    cli = args.ard_spec_dir / "conformance" / "bin" / "conformance-test"
    report = [f"## ARD conformance against `{base_url}`\n\nard-spec ref: `{args.ard_spec_ref}`\n"]
    try:
        if not cli.is_file():
            raise RuntimeError(f"conformance CLI not found at {cli}")
        wait_until_ready(base_url, args.ready_timeout)
        names = seed(base_url, args.seed_dir)
        check_published(base_url, names)
        results = [run_cli(cli, "manifest", f"{base_url}/.well-known/ard.json", report),
                   run_cli(cli, "registry", f"{base_url}/.well-known/ard", report)]
        passed = all(results)
    except (RuntimeError, urllib.error.URLError, OSError, ValueError) as error:
        print(f"::error::ARD conformance setup failed: {error}")
        report.append(f"**Setup failed:** {error}\n")
        passed = False

    report.append(f"\n**Overall: {'PASS' if passed else 'FAIL'}**\n")
    text = "\n".join(report)
    if args.report:
        args.report.write_text(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(text)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
