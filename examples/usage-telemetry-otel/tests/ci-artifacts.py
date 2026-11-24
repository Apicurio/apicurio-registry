#!/usr/bin/env python3
"""Fail-closed collector of the CI artifacts of this example.

Only files this invocation produced and that an explicit allowlist names are copied into the output directory, and each is
verified first (freshness, size, file type, a safe trace's shape, secret patterns). A file that is not on the allowlist is
excluded and listed; a file that is on it but fails verification, a symbolic link, a directory outside its root or a stale
file is a violation: it is not published and the exit status is 1. Nothing is read from anywhere the caller did not name,
and the browser tools' own report trees are never copied wholesale.

Usage: ci-artifacts.py --out DIR --start EPOCH --ui-dir DIR [--run-dir DIR ...] [--report-dir DIR ...]
                       [--logs DIR] [--components DIR] [--summary FILE]
  --out         new or empty directory that receives the published files and MANIFEST.json
  --start       epoch seconds at which the suite started; nothing older than that is accepted
  --ui-dir      the example's browser tests directory: run directories must be below its test-results, report
                directories below its playwright-report
  --run-dir     one run directory this invocation printed (results.json, screenshots/, pw/*/test-failed-N.png, trace.zip)
  --report-dir  one report directory this invocation printed (index.html and data/<sha1>.png|md|txt|zip)
  --logs        directory of the phase logs (<phase>.log), --components the container logs (<project>/<container>.log)
  --summary     the suite's own summary file
Exit status: 0 when there is no violation, 1 on a violation, 2 on a usage error. Python 3.8 or later, standard library only.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import zipfile
from calendar import timegm
from time import strptime

MAX_FILE = 8 * 1024 * 1024
MAX_TEXT = 1024 * 1024
LOG_TAIL = 2 * 1024 * 1024
MAX_TRACE = 64 * 1024 * 1024
MAX_TRACE_UNPACKED = 256 * 1024 * 1024
MAX_FILES = 400
RUN_NAME = re.compile(r"^(\d{8}T\d{6}Z)-\d{1,10}-[a-z0-9-]{1,30}$")
SLOT = r"[A-Za-z0-9][A-Za-z0-9._-]{0,200}"

# What must never leave the runner, wherever it appears in an allowlisted file
SECRET_PATTERNS = [
    ("an Authorization, Cookie or Set-Cookie header", re.compile(rb"(?i)\b(authorization|set-cookie|cookie)\s*:\s*\S")),
    ("a Grafana session cookie", re.compile(rb"(?i)grafana_session")),
    ("a bearer token", re.compile(rb"(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{16,}")),
    ("a JSON web token", re.compile(rb"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}")),
    ("a private key", re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("a GitHub or AWS credential", re.compile(rb"\b(gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16})\b")),
]
PNG = b"\x89PNG\r\n\x1a\n"
JPEG = b"\xff\xd8\xff"

RUN_FILES = [
    (re.compile(r"^results\.json$"), "json"),
    (re.compile(r"^screenshots/(overview|filtered)\.png$"), "png"),
    (re.compile(r"^pw/" + SLOT + r"/test-failed-[0-9]{1,3}\.png$"), "png"),
    (re.compile(r"^pw/" + SLOT + r"/trace\.zip$"), "trace"),
    (re.compile(r"^pw/" + SLOT + r"/error-context\.md$"), "text"),
]
REPORT_FILES = [
    (re.compile(r"^index\.html$"), "html"),
    (re.compile(r"^data/[0-9a-f]{40}\.png$"), "png"),
    (re.compile(r"^data/[0-9a-f]{40}\.(md|txt)$"), "text"),
    (re.compile(r"^data/[0-9a-f]{40}\.zip$"), "trace"),
]
TRACE_ENTRIES = [
    re.compile(r"^trace\.(trace|stacks)$"),
    re.compile(r"^trace\.network$"),
    re.compile(r"^aria/[A-Za-z0-9@_.-]{1,200}\.json$"),
    re.compile(r"^screenshots/[A-Za-z0-9@_.-]{1,200}\.png$"),
    re.compile(r"^screencast/[A-Za-z0-9@_.-]{1,200}\.jpeg$"),
]
TRACE_TEXT = re.compile(r"^(trace\.(trace|stacks)|aria/.*\.json)$")


class Collector:
    def __init__(self, out, start):
        self.out = out
        self.start = start
        self.published = []
        self.excluded = []
        self.violations = []

    # --- recording -------------------------------------------------------------------------------------------------
    def violation(self, what, why):
        self.violations.append({"path": what, "reason": why})

    def exclude(self, what, why):
        self.excluded.append({"path": what, "reason": why})

    def publish(self, rel, data, kind, origin):
        if len(self.published) >= MAX_FILES:
            self.violation(origin, "more than %d files would be published" % MAX_FILES)
            return
        target = os.path.join(self.out, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "wb") as handle:
            handle.write(data)
        self.published.append({"path": rel, "kind": kind, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})

    # --- checks ----------------------------------------------------------------------------------------------------
    def secrets_in(self, data):
        return [name for name, pattern in SECRET_PATTERNS if pattern.search(data)]

    def fresh(self, path):
        return os.lstat(path).st_mtime >= self.start

    def read_checked(self, path, label, limit):
        """The bytes of a regular, fresh, small-enough file, or None after recording why not."""
        info = os.lstat(path)
        if not os.path.isfile(path) or os.path.islink(path):
            self.violation(label, "not a regular file (a symbolic link or another kind of entry)")
            return None
        if info.st_mtime < self.start:
            self.violation(label, "older than this invocation (stale)")
            return None
        if info.st_size > limit:
            self.violation(label, "larger than the %d byte limit" % limit)
            return None
        with open(path, "rb") as handle:
            return handle.read()

    def check_content(self, kind, data, label):
        if kind in ("json",):
            try:
                json.loads(data.decode("utf-8"))
            except ValueError:
                self.violation(label, "not valid UTF-8 JSON")
                return False
        elif kind == "png":
            if not data.startswith(PNG):
                self.violation(label, "not a PNG image")
                return False
            return True
        elif kind in ("text", "html"):
            try:
                data.decode("utf-8")
            except ValueError:
                self.violation(label, "not UTF-8 text")
                return False
        found = self.secrets_in(data)
        if found:
            self.violation(label, "contains " + ", ".join(found))
            return False
        return True

    def check_trace(self, path, label):
        """A trace archive of the safe mode: only its known entries, no network, no secret in its text."""
        info = os.lstat(path)
        if info.st_size > MAX_TRACE:
            self.violation(label, "trace larger than the %d byte limit" % MAX_TRACE)
            return False
        try:
            archive = zipfile.ZipFile(path)
        except (zipfile.BadZipFile, OSError):
            self.violation(label, "not a readable zip archive")
            return False
        with archive:
            if sum(entry.file_size for entry in archive.infolist()) > MAX_TRACE_UNPACKED:
                self.violation(label, "trace unpacks to more than %d bytes" % MAX_TRACE_UNPACKED)
                return False
            for entry in archive.infolist():
                name = entry.filename
                if not any(pattern.match(name) for pattern in TRACE_ENTRIES):
                    self.violation(label, "an entry outside the safe trace shape: " + name)
                    return False
                if name == "trace.network" and entry.file_size != 0:
                    self.violation(label, "the trace records network activity")
                    return False
                content = archive.read(name)
                if name.endswith(".png") and not content.startswith(PNG):
                    self.violation(label, "trace entry %s is not a PNG image" % name)
                    return False
                if name.endswith(".jpeg") and not content.startswith(JPEG):
                    self.violation(label, "trace entry %s is not a JPEG image" % name)
                    return False
                if TRACE_TEXT.match(name):
                    found = self.secrets_in(content)
                    if found:
                        self.violation(label, "trace entry %s contains %s" % (name, ", ".join(found)))
                        return False
        return True

    # --- directories -----------------------------------------------------------------------------------------------
    def clean_dir(self, path, root, label, must_have_name=True):
        """The directory, when it really is a directory directly below root, spelled without links, named like a run and fresh."""
        real = os.path.realpath(path)
        if os.path.abspath(path) != real or os.path.dirname(real) != os.path.realpath(root) or not os.path.isdir(real):
            self.violation(label, "not a directory directly below " + root + ", or reached through a link")
            return None
        match = RUN_NAME.match(os.path.basename(real))
        if must_have_name and not match:
            self.violation(label, "its name is not a run directory name")
            return None
        if match:
            if timegm(strptime(match.group(1), "%Y%m%dT%H%M%SZ")) + 1 < self.start:
                self.violation(label, "a run that started before this invocation (stale)")
                return None
        return real

    def walk(self, base):
        files = []
        for directory, names, filenames in os.walk(base, followlinks=False):
            names.sort()
            for name in sorted(names):
                if os.path.islink(os.path.join(directory, name)):
                    self.violation(os.path.relpath(os.path.join(directory, name), base), "a symbolic link")
            for name in sorted(filenames):
                files.append(os.path.relpath(os.path.join(directory, name), base))
        return files

    def collect_tree(self, base, allowlist, prefix, label):
        for rel in self.walk(base):
            path = os.path.join(base, rel)
            shown = "%s/%s" % (label, rel)
            if os.path.islink(path):
                self.violation(shown, "a symbolic link")
                continue
            kind = next((kind for pattern, kind in allowlist if pattern.match(rel)), None)
            if kind is None:
                reason = "raw run input, never published" if rel.endswith("input.sealed.json") else "not on the allowlist"
                self.exclude(shown, reason)
                continue
            if kind == "trace":
                if not self.fresh(path):
                    self.violation(shown, "older than this invocation (stale)")
                elif self.check_trace(path, shown):
                    with open(path, "rb") as handle:
                        self.publish(os.path.join(prefix, rel), handle.read(), "trace", shown)
                continue
            data = self.read_checked(path, shown, MAX_FILE if kind in ("json", "png", "html") else MAX_TEXT)
            if data is not None and self.check_content(kind, data, shown):
                self.publish(os.path.join(prefix, rel), data, kind, shown)

    def collect_logs(self, base, pattern, prefix, label):
        if base is None or not os.path.isdir(base):
            return
        for rel in self.walk(base):
            path = os.path.join(base, rel)
            shown = "%s/%s" % (label, rel)
            if not pattern.match(rel):
                self.exclude(shown, "not on the allowlist")
                continue
            if os.path.islink(path):
                self.violation(shown, "a symbolic link")
                continue
            info = os.lstat(path)
            if info.st_mtime < self.start:
                self.violation(shown, "older than this invocation (stale)")
                continue
            with open(path, "rb") as handle:
                if info.st_size > LOG_TAIL:
                    handle.seek(-LOG_TAIL, os.SEEK_END)
                    data = ("[truncated: the last %d of %d bytes]\n" % (LOG_TAIL, info.st_size)).encode() + handle.read()
                else:
                    data = handle.read()
            found = self.secrets_in(data)
            if found:
                self.violation(shown, "contains " + ", ".join(found))
            else:
                self.publish(os.path.join(prefix, rel), data, "log", shown)


def main():
    parser = argparse.ArgumentParser(description="fail-closed collector of the CI artifacts", add_help=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--start", required=True, type=float)
    parser.add_argument("--ui-dir", required=True)
    parser.add_argument("--run-dir", action="append", default=[])
    parser.add_argument("--report-dir", action="append", default=[])
    parser.add_argument("--logs")
    parser.add_argument("--components")
    parser.add_argument("--summary")
    args = parser.parse_args()

    if os.path.lexists(args.out) and (not os.path.isdir(args.out) or os.listdir(args.out)):
        print("error: --out must be a new or empty directory: " + args.out, file=sys.stderr)
        return 2
    if not os.path.isabs(args.out) or not os.path.isabs(args.ui_dir):
        print("error: --out and --ui-dir must be absolute paths", file=sys.stderr)
        return 2
    os.makedirs(args.out, exist_ok=True)
    collector = Collector(args.out, args.start)

    results_root = os.path.join(args.ui_dir, "test-results")
    report_root = os.path.join(args.ui_dir, "playwright-report")
    for path in dict.fromkeys(args.run_dir):
        base = collector.clean_dir(path, results_root, "run " + os.path.basename(path))
        if base is not None:
            collector.collect_tree(base, RUN_FILES, os.path.join("ui", os.path.basename(base)), "run " + os.path.basename(base))
    for path in dict.fromkeys(args.report_dir):
        base = collector.clean_dir(path, report_root, "report " + os.path.basename(path))
        if base is not None:
            collector.collect_tree(base, REPORT_FILES, os.path.join("report", os.path.basename(base)), "report " + os.path.basename(base))
    collector.collect_logs(args.logs, re.compile(r"^[a-z0-9][a-z0-9-]{0,60}\.log$"), "logs", "logs")
    collector.collect_logs(args.components, re.compile(r"^[a-z0-9][a-z0-9._-]{0,100}/[A-Za-z0-9][A-Za-z0-9._-]{0,100}\.log$"), "components", "components")
    if args.summary is not None and os.path.lexists(args.summary):
        data = collector.read_checked(args.summary, "summary", MAX_TEXT)
        if data is not None and collector.check_content("text", data, "summary"):
            collector.publish("summary.tsv", data, "text", "summary")

    manifest = {
        "published": sorted(collector.published, key=lambda entry: entry["path"]),
        "excluded": sorted(collector.excluded, key=lambda entry: entry["path"]),
        "violations": sorted(collector.violations, key=lambda entry: (entry["path"], entry["reason"])),
    }
    with open(os.path.join(args.out, "MANIFEST.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("artifacts: %d published, %d excluded, %d violation(s)" % (len(manifest["published"]), len(manifest["excluded"]), len(manifest["violations"])))
    for entry in manifest["violations"]:
        print("VIOLATION %s: %s" % (entry["path"], entry["reason"]), file=sys.stderr)
    return 1 if manifest["violations"] else 0


if __name__ == "__main__":
    sys.exit(main())
