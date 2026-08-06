#!/usr/bin/env python3
"""Verify the app-module unit-test shards in verify-unit-tests.yaml partition the
test set exactly once.

Every surefire-eligible test class in app/ must be claimed by exactly one app-*
shard. A class claimed by none never runs in CI and fails silently, because the
workflow passes -Dsurefire.failIfNoSpecifiedTests=false. A class claimed by two
wastes a shard's budget.

Shards select tests one of two ways:
  - pattern shards use -Dtest=<comma-separated patterns>. Implements surefire's
    -Dtest= semantics: '!' prefix excludes, '**' matches across package
    separators, '*' matches within one segment. A filter of exclusions only
    includes everything not excluded.
  - tag shards use -Dgroups=<value>, matching classes annotated with
    @Tag(ApicurioTestTags.<CONST>) where CONST's string value is <value>. A
    class is only held to this check if it actually carries one of the group
    tags wired to a -Dgroups= shard (ApicurioTestTags.DOCKER/SLOW are not
    group tags and are ignored here); see issue #9302.

Usage:  python3 .github/scripts/verify-test-shards.py
Exits non-zero when the partition is broken.
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP_TESTS = REPO / "app/src/test/java"
WORKFLOW = REPO / ".github/workflows/verify-unit-tests.yaml"
TAGS_FILE = REPO / "utils/tests/src/main/java/io/apicurio/registry/utils/tests/ApicurioTestTags.java"

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


def is_abstract(path, source):
    return bool(re.search(r"\babstract\s+class\s+" + re.escape(path.stem) + r"\b", source))


def class_tags(source):
    """ApicurioTestTags constant names referenced via @Tag(ApicurioTestTags.X) in this source."""
    return set(re.findall(r"@Tag\(ApicurioTestTags\.(\w+)\)", source))


def enumerate_classes(group_consts):
    """Fully-qualified name -> source text, for every app/ test class that must be
    claimed by exactly one shard: surefire-eligible by naming convention, plus any
    class carrying a shard group tag (which surefire selects regardless of name)."""
    found = {}
    for path in APP_TESTS.rglob("*.java"):
        source = path.read_text(encoding="utf-8", errors="replace")
        if is_abstract(path, source):
            continue
        if not is_surefire_name(path.stem) and not (class_tags(source) & group_consts):
            continue
        fqn = str(path.relative_to(APP_TESTS)).replace("/", ".")[: -len(".java")]
        found[fqn] = source
    return dict(sorted(found.items()))


def load_tag_values():
    """ApicurioTestTags constant name -> string value, e.g. {'AUTH': 'auth', ...}."""
    text = TAGS_FILE.read_text(encoding="utf-8")
    return dict(re.findall(r'public static final String (\w+) = "([^"]+)";', text))


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
    """Read (name, kind, selector) triples straight from the workflow matrix.
    kind is 'tag' for a -Dgroups= shard (selector is the group value) or
    'pattern' for a -Dtest= shard (selector is the pattern list)."""
    shards, name = [], None
    for line in WORKFLOW.read_text(encoding="utf-8").splitlines():
        matched = re.match(r"\s*- name:\s*(\S+)", line)
        if matched:
            name = matched.group(1)
            continue
        matched = re.match(r'\s*test-filter:\s*"(.*)"\s*$', line)
        if matched and name:
            value = matched.group(1)
            groups_match = re.search(r"-Dgroups=(\S+)", value)
            if groups_match:
                shards.append((name, "tag", groups_match.group(1)))
            else:
                if value.startswith("-Dtest="):
                    value = value[len("-Dtest="):]
                shards.append((name, "pattern", value))
            name = None
    return [s for s in shards if s[0] != NON_APP_SHARD]


def claims(fqn, source, shards, const_by_value):
    hits = []
    for name, kind, selector in shards:
        if kind == "pattern":
            if matches(fqn, selector):
                hits.append(name)
        else:
            const = const_by_value.get(selector)
            if const and const in class_tags(source):
                hits.append(name)
    return hits


def main():
    shards = parse_shards()
    if not shards:
        print("ERROR: no shards parsed from", WORKFLOW)
        return 2

    tag_values = load_tag_values()
    const_by_value = {v: k for k, v in tag_values.items()}
    group_consts = set()
    for name, kind, selector in shards:
        if kind != "tag":
            continue
        const = const_by_value.get(selector)
        if const is None:
            print(f"ERROR: shard {name} selects -Dgroups={selector}, "
                  f"but no ApicurioTestTags constant has that value")
            return 2
        group_consts.add(const)

    classes = enumerate_classes(group_consts)

    print(f"app test classes (surefire-eligible or tagged, non-abstract): {len(classes)}")
    print(f"app shards: {', '.join(n for n, _, _ in shards)}\n")

    print(f"{'SHARD':<20} {'KIND':<8} {'CLASSES':>8}")
    for name, kind, selector in shards:
        count = sum(1 for fqn, source in classes.items()
                     if name in claims(fqn, source, [(name, kind, selector)], const_by_value))
        print(f"{name:<20} {kind:<8} {count:>8}")

    class_claims = {fqn: claims(fqn, source, shards, const_by_value) for fqn, source in classes.items()}
    orphans = sorted(fqn for fqn, hits in class_claims.items() if not hits)
    duplicates = {fqn: hits for fqn, hits in sorted(class_claims.items()) if len(hits) > 1}

    print(f"\nrun exactly once  : {sum(1 for h in class_claims.values() if len(h) == 1)}")
    print(f"run ZERO times    : {len(orphans)}")
    print(f"run MORE than once: {len(duplicates)}")

    if orphans:
        print("\n!! ORPHANS - these run in no shard and fail silently:")
        for fqn in orphans:
            print("    ", fqn)
    if duplicates:
        print("\n!! DUPLICATES - these run in several shards:")
        for fqn, hits in duplicates.items():
            print(f"     {fqn} -> {', '.join(hits)}")

    ok = not orphans and not duplicates
    print("\nRESULT:", "PARTITION OK" if ok else "PARTITION BROKEN")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
