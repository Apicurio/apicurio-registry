#!/usr/bin/env python3
"""Verify the app-module unit-test shards in verify-unit-tests.yaml partition the
test set exactly once.

Every surefire-eligible test class in app/ must be claimed by exactly one app-*
shard. A class claimed by none never runs in CI and fails silently, because the
workflow passes -Dsurefire.failIfNoSpecifiedTests=false. A class claimed by two
wastes a shard's budget.

Implements surefire's -Dtest= semantics: comma-separated fully-qualified-name
patterns, '!' prefix excludes, '**' matches across package separators, '*'
matches within one segment. A filter of exclusions only includes everything not
excluded.

Usage:  python3 .github/scripts/verify-test-shards.py
Exits non-zero when the partition is broken.
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP_TESTS = REPO / "app/src/test/java"
WORKFLOW = REPO / ".github/workflows/verify-unit-tests.yaml"

# The 'non-app' shard selects by Maven -pl, not by -Dtest, so it is not part of
# the app-module partition.
NON_APP_SHARD = "non-app"


def is_surefire_name(stem):
    """Match surefire's default includes."""
    return (
        stem.startswith("Test")
        or stem.endswith("Test")
        or stem.endswith("Tests")
        or stem.endswith("TestCase")
    )


def enumerate_classes():
    """Fully-qualified names of app/ test classes surefire would actually run."""
    found = []
    for path in APP_TESTS.rglob("*.java"):
        stem = path.stem
        if not is_surefire_name(stem):
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        # Abstract classes are bases for other tests; surefire never runs them.
        if re.search(r"\babstract\s+class\s+" + re.escape(stem) + r"\b", source):
            continue
        found.append(str(path.relative_to(APP_TESTS)).replace("/", ".")[:-len(".java")])
    return sorted(found)


def pattern_to_regex(pattern):
    regex, i = "", 0
    while i < len(pattern):
        if pattern.startswith("**", i):
            regex += ".*"
            i += 2
        elif pattern[i] == "*":
            regex += "[^.]*"
            i += 1
        else:
            regex += re.escape(pattern[i])
            i += 1
    return re.compile("^" + regex + "$")


def matches(fqn, test_filter):
    if not test_filter:
        return True
    patterns = [p.strip() for p in test_filter.split(",") if p.strip()]
    includes = [pattern_to_regex(p) for p in patterns if not p.startswith("!")]
    excludes = [pattern_to_regex(p[1:]) for p in patterns if p.startswith("!")]
    if includes and not any(r.match(fqn) for r in includes):
        return False
    return not any(r.match(fqn) for r in excludes)


def parse_shards():
    """Read (name, test-filter) pairs straight from the workflow matrix."""
    shards, name = [], None
    for line in WORKFLOW.read_text(encoding="utf-8").splitlines():
        matched = re.match(r"\s*- name:\s*(\S+)", line)
        if matched:
            name = matched.group(1)
            continue
        matched = re.match(r'\s*test-filter:\s*"(.*)"\s*$', line)
        if matched and name:
            value = matched.group(1)
            if value.startswith("-Dtest="):
                value = value[len("-Dtest="):]
            shards.append((name, value))
            name = None
    return [s for s in shards if s[0] != NON_APP_SHARD]


def main():
    classes = enumerate_classes()
    shards = parse_shards()
    if not shards:
        print("ERROR: no shards parsed from", WORKFLOW)
        return 2

    print(f"app test classes (surefire-eligible, non-abstract): {len(classes)}")
    print(f"app shards: {', '.join(n for n, _ in shards)}\n")

    print(f"{'SHARD':<20} {'CLASSES':>8}")
    for name, test_filter in shards:
        print(f"{name:<20} {sum(1 for c in classes if matches(c, test_filter)):>8}")

    claims = {c: [n for n, f in shards if matches(c, f)] for c in classes}
    orphans = sorted(c for c, hits in claims.items() if not hits)
    duplicates = {c: h for c, h in sorted(claims.items()) if len(h) > 1}

    print(f"\nrun exactly once  : {sum(1 for h in claims.values() if len(h) == 1)}")
    print(f"run ZERO times    : {len(orphans)}")
    print(f"run MORE than once: {len(duplicates)}")

    if orphans:
        print("\n!! ORPHANS - these run in no shard and fail silently:")
        for c in orphans:
            print("    ", c)
    if duplicates:
        print("\n!! DUPLICATES - these run in several shards:")
        for c, hits in duplicates.items():
            print(f"     {c} -> {', '.join(hits)}")

    ok = not orphans and not duplicates
    print("\nRESULT:", "PARTITION OK" if ok else "PARTITION BROKEN")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
