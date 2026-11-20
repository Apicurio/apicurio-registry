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
again. Why the location is what it is, and which routes to it are deliberately
not covered here, are in the kiota.binary.folder comment in the root pom.

Checked: the root pom's property, the profiles and the anchor that can move it
while leaving that property reading as expected, in the root pom and in the two
consumers, the os-maven-plugin extension that substitutes the classifier,
<targetBinaryFolder> on every execution in the two poms that run the plugin, and
-D on a workflow command line.

Poms are parsed rather than grepped because ElementTree ignores comments. A
line-oriented strip gets this wrong in both directions: it drops a live element
sharing its line with a comment opener, and keeps a commented-out setting whose
opener sits on an earlier line.
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
WORKFLOWS = ".github/workflows"
# Named rather than counted, so a module dropping the plugin is reported with
# the path that went missing. A count is satisfied by whichever one is left.
CONSUMERS = ("java-sdk/client/pom.xml", "java-sdk/client-v2/pom.xml")

# All three properties move the folder, each probed against the real pom with
# help:evaluate. The gap is matched once for both spellings rather than inside
# one alternative, since Maven accepts whitespace after either. The optional
# quote covers -D"kiota.binary.folder"=/x, which a shell strips before Maven
# sees it. No "=" is required: a valueless -Dkiota.binary.folder resolves the
# property to the literal "true" (probed on Maven 3.9.8), so that spelling
# moves the folder too. The trailing guard is (?![\w.-]) rather than \b so
# that neither the real Resolver property maven.repo.local.tail.threads nor a
# differently named one such as kiota.binary.folder-x is reported: \b ends at
# the dot after "local" and at the hyphen, reporting flags that move nothing.
OVERRIDE = re.compile(
    r"""(?:-D|--define[=\s])\s*["']?(?:{0}|{1}|{2})(?![\w.-])""".format(
        re.escape(PROPERTY), re.escape(REPOSITORY_PROPERTY),
        re.escape(ANCHOR_PROPERTY)))


def workflows():
    """Every workflow file, sorted, so findings come out in a stable order."""
    if not os.path.isdir(WORKFLOWS):
        return
    for name in sorted(os.listdir(WORKFLOWS)):
        if name.endswith((".yml", ".yaml")):
            yield WORKFLOWS + "/" + name


def properties_of(element, name=PROPERTY):
    """The named <properties> children of a pom element, in document order."""
    return element.findall("{*}properties/{*}" + name)


def parse(path):
    """The root element of an XML file, or a finding explaining why not.

    Returns (element, None) or (None, finding). A file can be missing or
    unreadable, so ET.parse raises OSError as well as ParseError; either way a
    lint step reports rather than dying with a traceback.
    """
    try:
        return ET.parse(path).getroot(), None
    except ET.ParseError as error:
        return None, "{0} is not valid XML: {1}".format(path, error)
    except OSError as error:
        return None, "Could not read {0}: {1}".format(path, error)


def profile_overrides(element):
    """(profile id, property) for every profile that moves the folder.

    A profile declaration leaves the top-level property reading as expected and
    still wins whenever the profile is active, so it is checked wherever poms
    are read: the root, where the value is defined, and the consumers, where it
    is interpolated.
    """
    for profile in element.findall("{*}profiles/{*}profile"):
        for moved in (PROPERTY, ANCHOR_PROPERTY):
            if properties_of(profile, moved):
                yield profile.findtext("{*}id", "with no id"), moved


def check_root_pom(project):
    """The property, the two ways to move it silently, and the extension."""
    declared = properties_of(project)
    if not declared:
        yield ("The root pom declares no <{0}>, so the modules running {1} have "
               "nothing to inherit. Maven passes the unresolved text to the "
               "plugin, which creates a directory of that name."
               .format(PROPERTY, PLUGIN))
    elif len(declared) > 1:
        # Maven keeps the last of a repeated property and does not warn, so a
        # check reading the first would report a value the build never uses.
        yield ("The root pom declares <{0}> {1} times. Maven uses the last one "
               "silently.".format(PROPERTY, len(declared)))
    else:
        # Compared against the one expected value rather than analysed. A prefix
        # test accepts ${settings.localRepository}/io/kiota, the artifact-shaped
        # path the pom comment exists to avoid, and the bare repository root.
        value = (declared[0].text or "").strip()
        if value != EXPECTED:
            yield ("<{0}> is {1}, expected {2}. See its comment in pom.xml."
                   .format(PROPERTY, value or "empty", EXPECTED))

    if properties_of(project, ANCHOR_PROPERTY):
        yield ("The root pom declares <{0}>, which relocates the whole local "
               "repository and takes the Kiota binary with it. Only "
               "~/.m2/repository is cached.".format(ANCHOR_PROPERTY))

    for name, moved in profile_overrides(project):
        yield ("Profile {0} in {1} overrides <{2}>, which wins over the "
               "declaration outside it whenever the profile is active."
               .format(name, ROOT_POM, moved))

    # Without the extension Maven substitutes nothing, so the plugin creates a
    # directory literally named ${os.detected.classifier} and every other check
    # here stays green.
    extensions = project.findall("{*}build/{*}extensions/{*}extension")
    if not any(e.findtext("{*}artifactId") == EXTENSION for e in extensions):
        yield ("The root pom registers no {0} build extension, so "
               "${{os.detected.classifier}} in <{1}> is never substituted."
               .format(EXTENSION, PROPERTY))


def check_consumers():
    """Every plugin execution reads the property rather than restating it.

    Hardcoding the path here is a one-line edit in the file a developer is
    already in, and it leaves the root pom green.

    Per execution rather than per file, so a second execution that forgets the
    setting is not covered by the first. Maven merges a plugin-level
    <configuration> into every execution, so that counts as set. The merge is
    read within one pom only, so hoisting the configuration into a parent's
    <pluginManagement> would report each child as setting nothing. That is a
    false positive rather than a miss, and no pom uses it today.
    """
    for path in CONSUMERS:
        project, failure = parse(path)
        if project is None:
            yield failure
            continue

        if properties_of(project):
            yield ("{0} redeclares <{1}>, which overrides the value it inherits "
                   "from the root pom.".format(path, PROPERTY))

        for name, moved in profile_overrides(project):
            yield ("Profile {0} in {1} overrides <{2}>, which wins over the "
                   "value the module inherits from the root pom whenever the "
                   "profile is active.".format(name, path, moved))

        found = False
        for plugin in project.findall(".//{*}plugin"):
            if plugin.findtext("{*}artifactId") != PLUGIN:
                continue
            found = True
            shared = plugin.find("{*}configuration/{*}" + SETTING)
            # Maven's implicit id for an execution declaring none is "default",
            # which is java-sdk/client's shape. A plugin with no executions is
            # described by where its configuration sits rather than by an id
            # that is not in the file.
            scopes = [("execution " + execution.findtext("{*}id", "default"),
                       execution.find("{*}configuration/{*}" + SETTING))
                      for execution in plugin.findall("{*}executions/{*}execution")]
            for where, own in scopes or [("the plugin declaration", None)]:
                setting = shared if own is None else own
                if setting is None:
                    yield ("{0}: {1} of {2} sets no <{3}>, so it downloads its "
                           "own binary into the plugin's default folder. If this "
                           "module inherits the configuration from a parent's "
                           "<pluginManagement>, this check cannot see it; set "
                           "<{3}> here.".format(path, where, PLUGIN, SETTING))
                elif (setting.text or "").strip() != CONSUMER_EXPRESSION:
                    yield ("{0}: {1} sets <{2}> to {3} rather than {4}, so that "
                           "module ignores the root pom."
                           .format(path, where, SETTING,
                                   (setting.text or "").strip() or "empty",
                                   CONSUMER_EXPRESSION))

        if not found:
            yield ("No plugin declaration of {0} in {1}. Either the generator is "
                   "wired up some other way now, or this check is looking in the "
                   "wrong place. Update CONSUMERS if a module was renamed or "
                   "genuinely stopped generating a client.".format(PLUGIN, path))


def strip_comment(line):
    """Drop a trailing # comment, so a flag written as prose is not reported.

    Only a # that starts a token opens a comment in YAML, so a value containing
    one is left alone. Quoting is not tracked, and no step in this tree needs it.
    """
    if "#" not in line:
        return line
    for index, character in enumerate(line):
        if character == "#" and (index == 0 or line[index - 1].isspace()):
            return line[:index]
    return line


def check_for_overrides(paths):
    """-D on a command line, which outranks every pom above."""
    for path in paths:
        try:
            with open(path, encoding="utf-8", errors="replace") as handle:
                lines = handle.readlines()
        except OSError as error:
            yield "Could not read {0}: {1}".format(path, error)
            continue

        for number, line in enumerate(lines, start=1):
            match = OVERRIDE.search(strip_comment(line))
            if match:
                yield ("{0}:{1} passes {2}, which outranks the pom property and "
                       "sends the binary somewhere the cache may not keep."
                       .format(path, number, match.group().strip()))


def main():
    root, failure = parse(ROOT_POM)
    if root is None:
        print(failure, file=sys.stderr)
        print("Run this from the repository root.", file=sys.stderr)
        return 1

    errors = list(check_root_pom(root))
    errors += list(check_consumers())
    errors += list(check_for_overrides(workflows()))

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print("Kiota binary folder ok: {0}".format(EXPECTED))
    return 0


if __name__ == "__main__":
    sys.exit(main())
