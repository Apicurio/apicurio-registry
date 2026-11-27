#!/usr/bin/env python3
"""Fail-closed collector of the CI artifacts of this example.

Only files this invocation produced and that an explicit allowlist names are copied into the output directory, and each is
verified first (freshness, size, file type, secret patterns, a configured credential in the file or in its published name). A file that is not on the allowlist is
excluded and counted; a file that is on it but fails verification, a symbolic link, a directory outside its root or a stale
file is a violation: it is not published and the exit status is 1. Nothing is read from anywhere the caller did not name,
and the public contract is narrow: the Playwright HTML report and its data tree, the raw Playwright results.json and the trace archives
are never published, because each embeds data in a form (base64, zip, free text of every test) that these checks cannot vet. They
stay on the runner. What is published from a run directory is two fixed screenshots, the failure screenshots, the error context of a
failed test and a numeric summary (results-summary.json: the counts and the duration of the run, numbers only, made from results.json).

Usage: ci-artifacts.py --out DIR --start EPOCH --ui-dir DIR [--run-dir DIR ...]
                       [--logs DIR] [--components DIR] [--summary FILE] [--private-report FILE]
  --out         new or empty directory that receives the published files and MANIFEST.json
  --start       epoch seconds at which the suite started; nothing older than that is accepted
  --ui-dir      the example's browser tests directory: run directories must be below its test-results
  --run-dir     one run directory this invocation printed (results.json is read for its counts only; screenshots/, pw/*/test-failed-N.png, pw/*/error-context.md)
  --logs        directory of the phase logs (<phase>.log), --components the container logs (<project>/<container>.log)
  --summary     the suite's own summary file; it is published with the collector's own row for this collection
  --private-report  a new file outside --out that receives the names and reasons of what was excluded or rejected; the published
                MANIFEST.json and the output of this program carry only counts
Exit status: 0 when there is no violation, 1 on a violation, 2 on a usage error. Python 3.8 or later, standard library only.
"""
import argparse
import base64
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
from calendar import timegm
from time import strptime
from urllib.parse import quote

MAX_FILE = 8 * 1024 * 1024
MAX_TEXT = 1024 * 1024
LOG_TAIL = 2 * 1024 * 1024
MAX_FILES = 400
RUN_NAME = re.compile(r"^(\d{8}T\d{6}Z)-\d{1,10}-[a-z0-9-]{1,30}$")
SLOT = r"[A-Za-z0-9][A-Za-z0-9._-]{0,200}"

# What must never leave the runner, wherever it appears in an allowlisted file
SECRET_PATTERNS = [
    # a header line, and the same header as a JSON or quoted key ("authorization": "...")
    ("an Authorization, Cookie or Set-Cookie header", re.compile(rb"(?i)\b(authorization|set-cookie|cookie)[\"']?\s*:\s*[\"']?\S")),
    ("a Grafana session cookie", re.compile(rb"(?i)grafana_session")),
    ("a bearer token", re.compile(rb"(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{16,}")),
    ("a JSON web token", re.compile(rb"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}")),
    ("a private key", re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("a GitHub or AWS credential", re.compile(rb"\b(gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16})\b")),
]
# The credentials this run is configured with: every variable named here (and in CI_REDACT_ENV, separated by spaces) that is present in the
# environment with a non-empty value is a secret, whatever its length and whatever it says. A short value, or one that equals a word such
# as "admin", is searched for like any other, so it withholds every file that holds it: the cost of a weak credential is a smaller upload,
# never a leak. Only a variable that is absent or empty is not scanned; the demo's documented default password is public because nothing
# puts it in the environment (it is the script's own fallback), not because of its value. The value, its percent-encoded and base64 forms
# and the base64 of admin:value are searched for.
DEFAULT_REDACT_ENV = ("GRAFANA_ADMIN_PASSWORD", "GF_SECURITY_ADMIN_PASSWORD")


def configured_secrets(environ):
    names = list(DEFAULT_REDACT_ENV) + environ.get("CI_REDACT_ENV", "").split()
    found = []
    for name in names:
        value = environ.get(name, "")
        if value == "":
            continue
        raw = value.encode("utf-8")
        forms = {raw, quote(value, safe="").encode(), base64.b64encode(raw), base64.b64encode(b"admin:" + raw)}
        found.extend(sorted(forms))
    return found


PNG = b"\x89PNG\r\n\x1a\n"
JPEG = b"\xff\xd8\xff"

RUN_FILES = [
    (re.compile(r"^results\.json$"), "counts"),
    (re.compile(r"^screenshots/(overview|filtered)\.png$"), "png"),
    (re.compile(r"^pw/" + SLOT + r"/test-failed-[0-9]{1,3}\.png$"), "png"),
    (re.compile(r"^pw/" + SLOT + r"/error-context\.md$"), "text"),
]


class Collector:
    def __init__(self, out, start):
        self.out = out
        self.start = start
        self.published = []
        self.excluded = []
        self.violations = []
        self.literals = configured_secrets(os.environ)

    # --- recording -------------------------------------------------------------------------------------------------
    def violation(self, what, why):
        self.violations.append({"path": what, "reason": why})

    def exclude(self, what, why):
        self.excluded.append({"path": what, "reason": why})

    def publish(self, rel, data, kind, origin):
        if len(self.published) >= MAX_FILES:
            self.violation(origin, "more than %d files would be published" % MAX_FILES)
            return
        if any(literal in rel.encode("utf-8") for literal in self.literals):
            self.violation(origin, "its published name holds a configured credential")
            return
        target = os.path.join(self.out, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "wb") as handle:
            handle.write(data)
        self.published.append({"path": rel, "kind": kind, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})

    # --- checks ----------------------------------------------------------------------------------------------------
    def secrets_in(self, data):
        found = [name for name, pattern in SECRET_PATTERNS if pattern.search(data)]
        if any(literal in data for literal in self.literals):
            found.append("a configured credential")
        return found

    def read_regular(self, path, label, limit, tail=False):
        """The bytes of a regular, fresh file read through one descriptor, or None after recording why not.

        The type is checked before anything is opened (a named pipe would block the open), the open never follows a link
        and does not wait for a writer, and the descriptor is checked to be the entry that was inspected. With tail, a
        file larger than the limit gives its last limit bytes; without it, a larger file is a violation."""
        try:
            info = os.lstat(path)
        except OSError:
            self.violation(label, "unreadable")
            return None
        if not stat.S_ISREG(info.st_mode):
            self.violation(label, "not a regular file (a symbolic link or another kind of entry)")
            return None
        if info.st_mtime < self.start:
            self.violation(label, "older than this invocation (stale)")
            return None
        if info.st_size > limit and not tail:
            self.violation(label, "larger than the %d byte limit" % limit)
            return None
        flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
        try:
            descriptor = os.open(path, flags)
        except OSError:
            self.violation(label, "could not be opened without following a link")
            return None
        with os.fdopen(descriptor, "rb") as handle:
            opened = os.fstat(handle.fileno())
            if not stat.S_ISREG(opened.st_mode) or (opened.st_dev, opened.st_ino) != (info.st_dev, info.st_ino):
                self.violation(label, "changed while it was being opened")
                return None
            if tail and opened.st_size > limit:
                handle.seek(-limit, os.SEEK_END)
                return ("[truncated: the last %d of %d bytes]\n" % (limit, opened.st_size)).encode() + handle.read(limit)
            data = handle.read(limit + 1)
        if len(data) > limit:
            self.violation(label, "larger than the %d byte limit" % limit)
            return None
        return data

    def run_counts(self, data, label):
        """The numbers of a Playwright results.json (the counts of tests and the duration), as the bytes of a new JSON file that
        holds nothing else: the raw file carries the title, location and error text of every test, which cannot be vetted."""
        try:
            stats = json.loads(data.decode("utf-8")).get("stats")
        except (ValueError, AttributeError):
            self.violation(label, "not a valid UTF-8 JSON object")
            return None
        if not isinstance(stats, dict):
            self.violation(label, "no stats object")
            return None
        counts = {}
        for key in ("expected", "unexpected", "flaky", "skipped", "duration"):
            value = stats.get(key)
            if isinstance(value, (int, float)) and not isinstance(value, bool) and 0 <= value < 10 ** 12:
                counts[key] = int(round(value))
        if not counts:
            self.violation(label, "no counts in the stats object")
            return None
        return (json.dumps(counts, sort_keys=True, indent=2) + "\n").encode("utf-8")

    def check_content(self, kind, data, label):
        if kind == "png":
            if not data.startswith(PNG):
                self.violation(label, "not a PNG image")
                return False
            return True
        elif kind == "text":
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
            try:
                started = timegm(strptime(match.group(1), "%Y%m%dT%H%M%SZ"))
            except ValueError:
                self.violation(label, "its name carries a date that does not exist")
                return None
            if started + 1 < self.start:
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
            data = self.read_regular(path, shown, MAX_FILE if kind in ("counts", "png") else MAX_TEXT)
            if data is None:
                continue
            if kind == "counts":
                summary = self.run_counts(data, shown)
                if summary is not None:
                    self.publish(os.path.join(prefix, "results-summary.json"), summary, "counts", shown)
                continue
            if self.check_content(kind, data, shown):
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
            data = self.read_regular(path, shown, LOG_TAIL, tail=True)
            if data is None:
                continue
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
    parser.add_argument("--logs")
    parser.add_argument("--components")
    parser.add_argument("--summary")
    parser.add_argument("--private-report", help="absolute path, outside --out, of a new file that receives the names and reasons")
    args = parser.parse_args()

    if os.path.lexists(args.out) and (not os.path.isdir(args.out) or os.listdir(args.out)):
        print("error: --out must be a new or empty directory: " + args.out, file=sys.stderr)
        return 2
    if not os.path.isabs(args.out) or not os.path.isabs(args.ui_dir):
        print("error: --out and --ui-dir must be absolute paths", file=sys.stderr)
        return 2
    if args.private_report is not None:
        parent = os.path.realpath(os.path.dirname(args.private_report))
        out_real = os.path.realpath(args.out)
        if not os.path.isabs(args.private_report) or os.path.lexists(args.private_report) or parent == out_real or parent.startswith(out_real + os.sep):
            print("error: --private-report must be a new absolute path outside --out", file=sys.stderr)
            return 2
    # The collection is built in a staging directory beside --out and renamed into place when it is complete, so a collector that is
    # interrupted at any point leaves --out absent, never half filled
    os.makedirs(os.path.dirname(os.path.normpath(args.out)), exist_ok=True)
    staging = tempfile.mkdtemp(prefix="." + os.path.basename(os.path.normpath(args.out)) + ".staging.", dir=os.path.dirname(os.path.normpath(args.out)))
    collector = Collector(staging, args.start)

    results_root = os.path.join(args.ui_dir, "test-results")
    for path in dict.fromkeys(args.run_dir):
        base = collector.clean_dir(path, results_root, "run " + os.path.basename(path))
        if base is not None:
            collector.collect_tree(base, RUN_FILES, os.path.join("ui", os.path.basename(base)), "run " + os.path.basename(base))
    collector.collect_logs(args.logs, re.compile(r"^[a-z0-9][a-z0-9-]{0,60}\.log$"), "logs", "logs")
    collector.collect_logs(args.components, re.compile(r"^[a-z0-9][a-z0-9._-]{0,100}/[A-Za-z0-9][A-Za-z0-9._-]{0,100}\.log$"), "components", "components")
    if args.summary is not None and os.path.lexists(args.summary):
        data = collector.read_regular(args.summary, "summary", MAX_TEXT)
        if data is not None and collector.check_content("text", data, "summary"):
            # The suite's own table cannot know how this collection ends; the row for it is the collector's, so the published
            # table and the exit status agree (a violation anywhere above is a fail here, whatever the caller recorded)
            rows = [row for row in data.decode("utf-8").splitlines() if not row.startswith("artifacts\t")]
            rows.append("artifacts\t%s\t0" % ("pass" if not collector.violations else "fail(1)"))
            collector.publish("summary.tsv", ("\n".join(rows) + "\n").encode("utf-8"), "text", "summary")

    # The published manifest names only what was published (names the allowlist's own patterns produced). What was excluded or
    # rejected is a count here: a raw name or reason would carry text from the files being judged out of the artifact.
    manifest = {
        "published": sorted(collector.published, key=lambda entry: entry["path"]),
        "excluded_count": len(collector.excluded),
        "violation_count": len(collector.violations),
    }
    with open(os.path.join(staging, "MANIFEST.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, sort_keys=True)
        handle.write("\n")
    if args.private_report is not None:
        detail = {
            "excluded": sorted(collector.excluded, key=lambda entry: entry["path"]),
            "violations": sorted(collector.violations, key=lambda entry: (entry["path"], entry["reason"])),
        }
        with open(args.private_report, "w", encoding="utf-8") as handle:
            json.dump(detail, handle, indent=2, sort_keys=True)
            handle.write("\n")
    if os.path.isdir(args.out):
        os.rmdir(args.out)
    os.rename(staging, args.out)
    print("artifacts: %d published, %d excluded, %d violation(s)" % (len(manifest["published"]), manifest["excluded_count"], manifest["violation_count"]))
    return 1 if collector.violations else 0


if __name__ == "__main__":
    sys.exit(main())
