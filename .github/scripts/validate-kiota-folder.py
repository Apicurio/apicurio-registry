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

Routes that can move the binary, and how each is covered:

  - the root pom's property, which has to be the one expected value
  - the os-maven-plugin extension, without which ${os.detected.classifier} is
    never substituted and the plugin creates a directory of that literal name
  - settings.localRepository redefined in a pom, which moves what the value is
    relative to
  - a profile in any pom, which overrides the property when it activates
  - the same property redeclared in a module pom, which overrides what that
    module inherits
  - <targetBinaryFolder> on an execution of the plugin, which is where the
    value is consumed
  - -D on the command line, which outranks all of the above, in a shell script,
    a Makefile, a Dockerfile, .mvn/*.config, a workflow or a composite action
  - <localRepository> in a settings.xml committed under .github/

One route stays open. A settings.xml supplied by the runner rather than by the
tree can name its own <localRepository>, or set the property in an active
profile, and nothing here can see either. The committed .github/ settings files
are checked for the first of those; the ones Maven picks up from ~/.m2 or from
a -s path outside the tree are not.

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
EXTENSION = "os-maven-plugin"
SETTING = "targetBinaryFolder"
ROOT_POM = "pom.xml"
SETTINGS_GLOB = ".github"

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
# after -D and accepts --define in both its spellings, so all are matched. The
# optional quote covers -D"kiota.binary.folder"=/x, which a shell strips before
# Maven ever sees it.
# The trailing guard is (?![\w.]) rather than \b so that the real Resolver
# properties maven.repo.local.tail.threads and maven.repo.local.record.reverseTree
# do not match: \b ends the match at the dot after "local" and reports a flag that
# does not move anything.
OVERRIDE = re.compile(
    r"""(?:-D\s*|--define[=\s])["']?(?:{0}|{1})(?![\w.])""".format(
        re.escape(PROPERTY), re.escape(REPOSITORY_PROPERTY)))

# Directories whose contents are generated or vendored rather than written.
# Deliberately narrower than .gitignore, which also lists python-sdk/kiota_tmp,
# python-sdk/dist, docs/.jbang/ and **/bin. Reading .gitignore instead would be
# the self-updating option, but the tests build fixture trees outside any git
# repository, so nothing here can ask git what is ignored. Walking one of those
# directories costs a wasted read, not a wrong answer.
PRUNED = frozenset((".git", "target", "node_modules", "__pycache__", ".venv"))


def walk(root="."):
    """Every file in the tree, skipping generated and vendored directories.

    Paths come back with forward slashes on every platform. invokes_maven
    matches on a leading .mvn/ or .github/, and on Windows a native separator
    would quietly reduce the scan to .sh files.
    """
    for directory, subdirs, filenames in os.walk(root):
        subdirs[:] = sorted(d for d in subdirs if d not in PRUNED)
        for filename in sorted(filenames):
            relative = os.path.relpath(os.path.join(directory, filename), root)
            yield relative.replace(os.sep, "/")


def invokes_maven(path):
    """Whether a file can put a -D on a Maven command line.

    Scoping by what a file does, rather than by which directory it sits in, is
    what lets this script and its test write the flags out in full. It also
    keeps prose that merely mentions a flag from failing the build, which
    matters because DEVELOPING.md documents this one.

    Makefiles and Dockerfiles are in scope because they really do run Maven
    here: operator/Makefile runs mvn clean install and mvn verify, and four
    Dockerfiles run mvn package. Both use # for comments, so strip_comment
    applies to them unchanged.
    """
    name = os.path.basename(path)
    if name.endswith((".sh", ".bash")):
        return True
    if name == "Makefile" or name.startswith("Dockerfile"):
        return True
    # Covers both .mvn/maven.config and .mvn/jvm.config.
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


def parse(path):
    """The root element of an XML file, or a finding explaining why not.

    Returns (element, None) or (None, finding). os.walk lists a dangling
    symlink as a file, and a file can be unreadable, so ET.parse raises OSError
    as well as ParseError. Either way a lint step reports rather than crashing
    with a traceback that says nothing about what to fix.
    """
    try:
        return ET.parse(path).getroot(), None
    except ET.ParseError as error:
        return None, "{0} is not valid XML: {1}".format(path, error)
    except OSError as error:
        return None, "Could not read {0}: {1}".format(path, error)


def check_root_pom(project):

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

    # The classifier segment is only a per-platform directory while something
    # sets os.detected.*. Drop the extension and Maven substitutes nothing, so
    # the plugin creates a directory literally named ${os.detected.classifier}
    # and every other check here stays green.
    extensions = project.findall("{*}build/{*}extensions/{*}extension")
    if not any(e.findtext("{*}artifactId") == EXTENSION for e in extensions):
        yield ("The root pom registers no {0} build extension, so "
               "${{os.detected.classifier}} in <{1}> is never substituted."
               .format(EXTENSION, PROPERTY))


def check_pom_overrides(path, project):
    """Ways a pom can move the folder without touching the root declaration."""
    if project.findall("{*}properties/{*}" + ANCHOR_PROPERTY):
        yield ("{0} declares <{1}>, which beats the real local repository path "
               "and moves the binary out of any cache of ~/.m2/repository."
               .format(path, ANCHOR_PROPERTY))

    # A module redeclaring the property overrides what it inherits, and the root
    # pom it is checked against stays untouched.
    if path != ROOT_POM and properties_of(project):
        yield ("{0} redeclares <{1}>, which overrides the value it inherits "
               "from the root pom.".format(path, PROPERTY))

    for profile in project.findall("{*}profiles/{*}profile"):
        if properties_of(profile) or profile.findall(
                "{*}properties/{*}" + ANCHOR_PROPERTY):
            name = profile.findtext("{*}id", "<no id>")
            yield ("Profile {0} in {1} overrides <{2}>. A profile that "
                   "activates on the runner defeats the property with the "
                   "declaration above it left untouched."
                   .format(name, path, PROPERTY))


def check_settings(paths):
    """A committed settings.xml can move the whole local repository."""
    for path in paths:
        settings, failure = parse(path)
        if failure:
            yield failure
            continue

        if settings.tag.rpartition("}")[2] != "settings":
            continue
        if settings.findall("{*}localRepository"):
            yield ("{0} names its own <localRepository>, which moves the whole "
                   "repository out from under the cache this folder lives in."
                   .format(path))
        for profile in settings.findall("{*}profiles/{*}profile"):
            if properties_of(profile):
                name = profile.findtext("{*}id", "<no id>")
                yield ("Profile {0} in {1} sets <{2}>, and a settings profile "
                       "outranks the pom.".format(name, path, PROPERTY))


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

    Each pom is also passed to check_pom_overrides here rather than in a pass of
    its own, so the tree is parsed once.
    """
    configured = False
    for path in paths:
        project, failure = parse(path)
        if failure:
            yield failure
            continue

        for finding in check_pom_overrides(path, project):
            yield finding

        for plugin in project.findall(".//{*}plugin"):
            # A descendant search, so a declaration under <pluginManagement>
            # counts too. That is stricter than Maven, which runs nothing from
            # there, and no pom declares this plugin that way today.
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
    containing one is left alone. A # inside quotes is not an opener either, and
    that is worth tracking rather than assuming: a step that echoes an issue
    number or a git --format string before running the build would otherwise
    have its whole command line discarded and its -D never seen.

    A quote is only treated as opening when its partner appears later on the
    line. An apostrophe in prose is far more common than an unterminated string,
    and mistaking one for a quote would swallow the rest of the line.
    """
    quote = ""
    for index, character in enumerate(line):
        if quote:
            if character == quote:
                quote = ""
        elif character in "\"'":
            if character in line[index + 1:]:
                quote = character
        elif character == "#" and (index == 0 or line[index - 1].isspace()):
            return line[:index]
    return line


def main():
    # lexists rather than isfile, so that a pom.xml which is present but not
    # readable reaches parse and gets told apart from one that is absent. isfile
    # is False for a dangling symlink, which would report the wrong cause.
    if not os.path.lexists(ROOT_POM):
        print("No {0} here. Run this from the repository root.".format(ROOT_POM),
              file=sys.stderr)
        return 1

    root, failure = parse(ROOT_POM)
    if failure:
        print(failure, file=sys.stderr)
        return 1
    errors = list(check_root_pom(root))

    poms, invokers, settings = [], [], []
    for path in walk():
        name = os.path.basename(path)
        if name == "pom.xml":
            poms.append(path)
        elif path.startswith(SETTINGS_GLOB + "/") and name.endswith(".xml"):
            settings.append(path)
        elif invokes_maven(path):
            invokers.append(path)

    errors += list(check_consumers(poms))
    errors += list(check_settings(settings))
    errors += list(check_for_overrides(invokers))

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print("Kiota binary folder ok: {0}".format(EXPECTED))
    return 0


if __name__ == "__main__":
    sys.exit(main())
