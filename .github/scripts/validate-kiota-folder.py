#!/usr/bin/env python3
# Copyright 2026 Red Hat
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Check where the Kiota generator binary is cached.

Moving the folder back under target/ costs nothing at build time and reports no
error. The build still passes, it is just slower, until github.com returns a 504
again. See the kiota.binary.folder comment in the root pom for why the location
is what it is.

Five routes can move the binary, and each is checked here:

  - the root pom's property, which has to be the one expected value
  - settings.localRepository redefined in the pom, which moves what that value
    is relative to
  - a profile in the root pom, which overrides the property when it activates
  - <targetBinaryFolder> on an execution of the plugin, which is where the
    value is consumed
  - -D on the command line, which outranks all of the above

A settings.xml naming its own <localRepository> is the one route left open. It
would move the whole repository out from under the scheme, and the file is
supplied by the runner rather than the tree, so nothing here can see it.

Poms are parsed rather than grepped because ElementTree ignores comments. A
line-oriented strip gets this wrong in both directions: it drops a live element
that shares its line with a comment opener, and it keeps a commented-out setting
whose opener sits on an earlier line.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

PROPERTY = "kiota.binary.folder"
REPOSITORY_PROPERTY = "maven.repo.local"
ANCHOR_PROPERTY = "settings.localRepository"
EXPECTED = "${settings.localRepository}/.cache/kiota-binary/${os.detected.classifier}"
CONSUMER_EXPRESSION = "${" + PROPERTY + "}"
PLUGIN = "kiota-maven-plugin"
SETTING = "targetBinaryFolder"
ROOT_POM = "pom.xml"

# Comparing against the one expected value rather than analysing an arbitrary one
# also enforces the reason for it. A prefix test accepts
# ${settings.localRepository}/io/kiota, which is the artifact-shaped path the pom
# comment exists to avoid, and ${settings.localRepository} with no suffix at all.
# The classifier segment is part of the expected value for the same reason: drop
# it and one local repository shared between a container and its host resolves
# to a single binary built for whichever platform downloaded it first.

# -D outranks the pom wherever it appears, and MAVEN_ARGS in a composite action
# carries the same text. Relocating the whole local repository moves the folder
# with it, and only ~/.m2/repository is saved to the cache. Maven accepts a space
# after -D and accepts --define, so both spellings are matched.
OVERRIDE = re.compile(
    r"(?:-D\s*|--define\s+)(?:{0}|{1})\b".format(
        re.escape(PROPERTY), re.escape(REPOSITORY_PROPERTY)))

# Directories whose contents are generated or vendored rather than written.
# Deliberately narrower than .gitignore, which also lists python-sdk/kiota_tmp,
# python-sdk/dist, docs/.jbang/ and **/bin. Reading .gitignore instead would be
# the self-updating option, but the tests build fixture trees outside any git
# repository, so nothing here can ask git what is ignored. Walking one of those
# directories costs a wasted read, not a wrong answer.
PRUNED = frozenset((".git", "target", "node_modules", "__pycache__", ".venv"))


def walk(root="."):
    """Every file in the tree, skipping generated and vendored directories."""
    for directory, subdirs, filenames in os.walk(root):
        subdirs[:] = sorted(d for d in subdirs if d not in PRUNED)
        for filename in sorted(filenames):
            yield os.path.relpath(os.path.join(directory, filename), root)


def invokes_maven(path):
    """Whether a file can put a -D on a Maven command line.

    Scoping by what a file does, rather than by which directory it sits in, is
    what lets this script and its test write the flags out in full. It also
    keeps prose that merely mentions a flag from failing the build, which
    matters because DEVELOPING.md documents this one.
    """
    name = os.path.basename(path)
    if name.endswith(".sh"):
        return True
    if path.startswith(".mvn/") and name.endswith(".config"):
        return True
    if path.startswith(".github/workflows/") and name.endswith((".yml", ".yaml")):
        return True
    if path.startswith(".github/actions/") and name in ("action.yml", "action.yaml"):
        return True
    return False


def properties_of(element):
    """The <properties> children of a pom element, in document order."""
    return element.findall("{*}properties/{*}" + PROPERTY)


def check_root_pom(path=ROOT_POM):
    project = ET.parse(path).getroot()

    declared = properties_of(project)
    if not declared:
        yield ("The root pom declares no <{0}>, so the modules running "
               "kiota-maven-plugin have nothing to inherit. Maven passes the "
               "unresolved text to the plugin, which creates a directory of "
               "that name.".format(PROPERTY))
    elif len(declared) > 1:
        # Maven keeps the last of a repeated property and does not warn, so a
        # check reading the first would report a value the build never uses.
        yield ("The root pom declares <{0}> {1} times. Maven uses the last one "
               "silently.".format(PROPERTY, len(declared)))
    else:
        value = (declared[0].text or "").strip()
        if value != EXPECTED:
            yield ("<{0}> is {1}, expected {2}. See its comment in pom.xml."
                   .format(PROPERTY, value or "empty", EXPECTED))

    if project.findall("{*}properties/{*}" + ANCHOR_PROPERTY):
        yield ("The root pom declares <{0}>, which beats the real local "
               "repository path and moves the binary out of any cache of "
               "~/.m2/repository.".format(ANCHOR_PROPERTY))

    for profile in project.findall("{*}profiles/{*}profile"):
        if properties_of(profile):
            name = profile.findtext("{*}id", "<no id>")
            yield ("Profile {0} in the root pom overrides <{1}>. A profile that "
                   "activates on the runner defeats the property with the "
                   "declaration above it left untouched.".format(name, PROPERTY))


def check_consumers(paths):
    """Every execution of the plugin has to read the property rather than restate it.

    This is where the value is actually consumed, and hardcoding it here is a
    one-line edit in the file a developer is already in when touching kiota
    configuration. It leaves the root pom, and this check's other half, green.

    Checked per execution rather than per file, because a second execution that
    forgets the setting would otherwise pass on the strength of the first one.
    Maven merges a plugin-level <configuration> into every execution, so that
    counts as set: the two consuming poms use one shape each.

    Yields a finding when no pom configures the plugin at all, because a check
    that silently matches nothing is the one that stops catching regressions.
    """
    configured = False
    for path in paths:
        try:
            project = ET.parse(path).getroot()
        except ET.ParseError as error:
            yield "{0} is not valid XML: {1}".format(path, error)
            continue

        for plugin in project.findall(".//{*}plugin"):
            if plugin.findtext("{*}artifactId") != PLUGIN:
                continue
            configured = True
            shared = plugin.find("{*}configuration/{*}" + SETTING)
            scopes = [(execution.findtext("{*}id", "<no id>"),
                       execution.find("{*}configuration/{*}" + SETTING))
                      for execution in plugin.findall("{*}executions/{*}execution")]
            for name, own in scopes or [("plugin", None)]:
                setting = shared if own is None else own
                if setting is None:
                    yield ("{0}: execution {1} of {2} sets no <{3}>, so it "
                           "downloads its own binary into the plugin's default "
                           "folder.".format(path, name, PLUGIN, SETTING))
                    continue
                value = (setting.text or "").strip()
                if value != CONSUMER_EXPRESSION:
                    yield ("{0}: execution {1} sets <{2}> to {3} rather than "
                           "{4}, so that module ignores the root pom."
                           .format(path, name, SETTING, value or "empty",
                                   CONSUMER_EXPRESSION))

    if not configured:
        yield ("No pom configures {0}. Either the generator is wired up some "
               "other way now, or this check is looking in the wrong place."
               .format(PLUGIN))


def check_for_overrides(paths):
    for path in paths:
        try:
            with open(path, encoding="utf-8", errors="replace") as handle:
                lines = handle.readlines()
        except FileNotFoundError:
            # os.walk lists a dangling symlink as a file. Nothing to read is
            # nothing to override.
            continue
        except OSError as error:
            yield "Could not read {0}: {1}".format(path, error)
            continue

        for number, line in enumerate(lines, start=1):
            match = OVERRIDE.search(strip_comment(line))
            if match:
                yield ("{0}:{1} passes {2}, which outranks the pom property and "
                       "sends the binary somewhere the cache may not keep."
                       .format(path, number, match.group().strip()))


def strip_comment(line):
    """Drop a trailing # comment, the form shared by YAML, shell and maven.config.

    Only a # that starts a token is a comment opener in all three, so a value
    containing one is left alone.
    """
    return re.split(r"(?:^|(?<=\s))#", line, maxsplit=1)[0]


def main():
    if not os.path.isfile(ROOT_POM):
        print("No {0} here. Run this from the repository root.".format(ROOT_POM),
              file=sys.stderr)
        return 1

    try:
        errors = list(check_root_pom())
    except ET.ParseError as error:
        print("{0} is not valid XML: {1}".format(ROOT_POM, error), file=sys.stderr)
        return 1

    poms, invokers = [], []
    for path in walk():
        if os.path.basename(path) == "pom.xml":
            poms.append(path)
        elif invokes_maven(path):
            invokers.append(path)

    errors += list(check_consumers(poms))
    errors += list(check_for_overrides(invokers))

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print("Kiota binary folder ok: {0}".format(EXPECTED))
    return 0


if __name__ == "__main__":
    sys.exit(main())
