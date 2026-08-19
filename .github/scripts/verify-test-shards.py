#!/usr/bin/env python3
"""Verify the app-module unit-test shards in verify-unit-tests.yaml partition the
test set exactly once.

Every non-abstract test class in app/ must be claimed by exactly one app-* shard.
A class claimed by none never runs in CI and fails silently, because the workflow
passes -Dsurefire.failIfNoSpecifiedTests=false. A class claimed by two wastes a
shard's budget. Classes are enumerated regardless of surefire naming convention
or @Tag -- a class that is neither convention-named nor tagged used to be
invisible to this check entirely rather than reported as an orphan; see the
review discussion on #9328.

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
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP_TESTS = REPO / "app/src/test/java"
WORKFLOW = REPO / ".github/workflows/verify-unit-tests.yaml"
TAGS_FILE = REPO / "utils/tests/src/main/java/io/apicurio/registry/utils/tests/ApicurioTestTags.java"

# The 'non-app' shard selects by Maven -pl, not by -Dtest, so it is not part of
# the app-module partition.
NON_APP_SHARD = "non-app"

# Packages that predate the tag-shard migration and are still excluded by name
# from app-other, but no longer have a pattern shard of their own -- coverage
# for them now depends entirely on carrying the matching @Tag. Used only to
# annotate orphans with a more actionable hint; does not affect claims().
TAG_SHARD_PACKAGES = {
    "app-auth": ("auth", "rbac", "limits"),
    "app-transport": ("tls", "headers", "cors"),
    "app-metrics": ("metrics", "search"),
}

TEST_METHOD_RE = re.compile(r"@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b")


def tag_shard_hint(fqn):
    """If fqn sits in a package that used to map 1:1 to a tag shard, name that
    shard and the tag it expects -- this is the AuthTestNoRoles-style orphan a
    missing @Tag produces, distinct from a genuinely unmapped new package."""
    parts = fqn.split(".")
    if len(parts) < 4 or parts[:3] != ["io", "apicurio", "registry"]:
        return None
    package = parts[3]
    for shard, packages in TAG_SHARD_PACKAGES.items():
        if package in packages:
            return shard
    return None


def is_abstract(path, source):
    return bool(re.search(r"\babstract\s+class\s+" + re.escape(path.stem) + r"\b", source))


def class_tags(source):
    """ApicurioTestTags constant names referenced via @Tag(ApicurioTestTags.X) in this source."""
    return set(re.findall(r"@Tag\(ApicurioTestTags\.(\w+)\)", source))


def is_surefire_name(stem):
    """Match surefire's default includes."""
    return (
        stem.startswith("Test")
        or stem.endswith("Test")
        or stem.endswith("Tests")
        or stem.endswith("TestCase")
    )


def looks_like_a_test(path, source):
    """Whether this class is plausibly something surefire would execute, by any
    of three independent signals: its own @Test-style method, a surefire
    naming-convention match, or an ApicurioTestTags @Tag. A class needs only
    one -- this deliberately stays permissive because a false *inclusion* here
    just adds a class to the partition check (worst case, it's a real gap that
    now gets reported), while a false *exclusion* silently recreates the
    invisible-orphan bug this script exists to catch.

    A single signal on its own isn't enough: some real test classes (e.g.
    MysqlStorageTest, KafkaSqlAuthTest) inherit every @Test method from an
    abstract base and carry none of their own, so they rely on the naming
    convention; a few real test classes (e.g. AuthTestNoRoles) don't match the
    naming convention and rely on their own @Test methods or @Tag instead."""
    return (
        is_surefire_name(path.stem)
        or bool(TEST_METHOD_RE.search(source))
        or bool(class_tags(source))
    )


def enumerate_classes():
    """Fully-qualified name -> source text, for every non-abstract .java file in
    app/'s test tree that looks like a test (see looks_like_a_test). This
    still catches the AuthTestNoRoles-style class that fails the naming
    convention but not the tag check -- and, unlike gating purely on naming
    convention or tag, it no longer silently excludes such a class from this
    check just because it happens to fail one particular signal. See the
    review discussion on #9328."""
    found = {}
    for path in APP_TESTS.rglob("*.java"):
        source = path.read_text(encoding="utf-8", errors="replace")
        if is_abstract(path, source):
            continue
        if not looks_like_a_test(path, source):
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
    kind is 'tag' for a shard carrying a non-empty group-filter (selector is the
    -Dgroups= value) or 'pattern' for a plain -Dtest= shard (selector is the
    pattern list). test-filter and group-filter are read from their own matrix
    keys rather than parsed out of one combined string, matching how the
    workflow's run step now passes them as two separate tokens."""
    shards, name, test_filter, group_filter = [], None, None, None

    def flush():
        if name is None:
            return
        groups_match = re.search(r"-Dgroups=(\S+)", group_filter or "")
        if groups_match:
            shards.append((name, "tag", groups_match.group(1)))
            return
        value = test_filter or ""
        if value.startswith("-Dtest="):
            value = value[len("-Dtest="):]
        shards.append((name, "pattern", value))

    for line in WORKFLOW.read_text(encoding="utf-8").splitlines():
        if re.match(r"^\s{0,7}steps:\s*$", line):
            # End of the matrix.shard list -- the job's own steps: follow, and
            # its "- name: <step>" entries must not be parsed as shards.
            break
        matched = re.match(r"\s*- name:\s*(\S+)", line)
        if matched:
            flush()
            name, test_filter, group_filter = matched.group(1), None, None
            continue
        matched = re.match(r'\s*test-filter:\s*"(.*)"\s*$', line)
        if matched:
            test_filter = matched.group(1)
            continue
        matched = re.match(r'\s*group-filter:\s*"(.*)"\s*$', line)
        if matched:
            group_filter = matched.group(1)
    flush()
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

    classes = enumerate_classes()

    print(f"app test classes (non-abstract): {len(classes)}")
    print(f"app shards: {', '.join(n for n, _, _ in shards)}\n")

    class_claims = {fqn: claims(fqn, source, shards, const_by_value) for fqn, source in classes.items()}
    shard_counts = Counter(name for hits in class_claims.values() for name in hits)
    orphans = sorted(fqn for fqn, hits in class_claims.items() if not hits)
    duplicates = {fqn: hits for fqn, hits in sorted(class_claims.items()) if len(hits) > 1}

    print(f"{'SHARD':<20} {'KIND':<8} {'CLASSES':>8}")
    for name, kind, selector in shards:
        print(f"{name:<20} {kind:<8} {shard_counts[name]:>8}")

    print(f"\nrun exactly once  : {sum(1 for h in class_claims.values() if len(h) == 1)}")
    print(f"run ZERO times    : {len(orphans)}")
    print(f"run MORE than once: {len(duplicates)}")

    group_by_shard = {name: selector for name, kind, selector in shards if kind == "tag"}
    if orphans:
        print("\n!! ORPHANS - these run in no shard and fail silently:")
        for fqn in orphans:
            hint = tag_shard_hint(fqn)
            expected_const = const_by_value.get(group_by_shard.get(hint)) if hint else None
            if expected_const:
                print("    ", fqn,
                      f"(sits in {hint}'s package but carries no @Tag(ApicurioTestTags.{expected_const}) "
                      f"-- add the tag or move the class)")
            else:
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
