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

"""Mutation tests for the Kiota binary folder check.

Each test builds a small tree, applies one change, and asserts the exit code.
The accepted cases carry as much weight as the rejected ones. An earlier version
of this check matched text rather than parsing, and it rejected a property whose
line also opened a comment while accepting one that was commented out.

Flags are written out in full here. The check scopes itself by what a file does
rather than by where it sits, and a .py file cannot put a -D on a Maven command
line, so this file is not scanned. test_a_python_file_is_not_scanned pins that.
"""

import contextlib
import importlib.util
import io
import os
import sys
import tempfile
import unittest

script_dir = os.path.dirname(os.path.abspath(__file__))
script_path = os.path.join(script_dir, "validate-kiota-folder.py")

spec = importlib.util.spec_from_file_location("validate_kiota_folder", script_path)
# The script's name has a hyphen in it, so a plain import cannot reach it and it
# is loaded from its path instead. spec and spec.loader are typed as optional
# because a finder is allowed to return nothing for a path it does not handle.
# A missing script here means the test file has been moved away from the script
# it tests, and saying so beats an AttributeError on None three lines down.
if spec is None or spec.loader is None:
    raise ImportError("Could not load {0}".format(script_path))
guard = importlib.util.module_from_spec(spec)
sys.modules["validate_kiota_folder"] = guard
spec.loader.exec_module(guard)

# Read from the script rather than restated here. A second copy of the expected
# value would let the two drift apart, and the tests would keep passing against
# whatever the script had been changed to.
GOOD_VALUE = guard.EXPECTED
EXPRESSION = guard.CONSUMER_EXPRESSION

# The namespace matters: the script matches elements with {*} wildcards, so a
# fixture that declared none would still parse and still pass, and would stop
# representing a real pom without any test noticing.
PREAMBLE = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
"""

ROOT_POM = PREAMBLE + """  <modelVersion>4.0.0</modelVersion>
  <artifactId>apicurio-registry</artifactId>
  <properties>
    <kiota.version>1.28.0</kiota.version>
{property}  </properties>
{profiles}  <build>{extensions}</build>
</project>
"""

EXTENSIONS = """<extensions><extension>
      <groupId>kr.motd.maven</groupId>
      <artifactId>os-maven-plugin</artifactId>
    </extension></extensions>"""

PROPERTY_LINE = "    <kiota.binary.folder>{0}</kiota.binary.folder>\n"

# A module pom carrying nothing but the one element a test is about. No
# assertion reads the artifactId, and the remaining elements of a real module
# pom would be scenery.
MODULE_POM = PREAMBLE + """  <artifactId>module</artifactId>
{body}</project>
"""

# java-sdk/client sets the folder on the plugin and java-sdk/client-v2 sets it on
# the execution. Maven merges plugin configuration into every execution, so both
# shapes are correct and both are exercised below.
CONSUMER_POM = PREAMBLE + """  <modelVersion>4.0.0</modelVersion>
  <artifactId>apicurio-registry-java-sdk</artifactId>
  <build><plugins><plugin>
    <groupId>io.kiota</groupId>
    <artifactId>kiota-maven-plugin</artifactId>
{shared}    <executions>
{executions}    </executions>
  </plugin></plugins></build>
</project>
"""

FOLDER = "<configuration><targetBinaryFolder>{0}</targetBinaryFolder></configuration>"
SHARED_FOLDER = "    " + FOLDER + "\n"
EXECUTION = "      <execution><id>{0}</id><goals><goal>generate</goal></goals>" \
            "{1}</execution>\n"


def execution(name, folder=""):
    """One <execution>, with its own <targetBinaryFolder> when given a value."""
    return EXECUTION.format(name, FOLDER.format(folder) if folder else "")


class KiotaFolderCheckTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        previous = os.getcwd()
        os.chdir(self.tmp.name)
        self.addCleanup(os.chdir, previous)
        self.write_root()
        self.write_consumer()

    def write(self, relative_path, content):
        full = os.path.join(self.tmp.name, relative_path)
        directory = os.path.dirname(full)
        if directory:
            os.makedirs(directory, exist_ok=True)
        with open(full, "w", encoding="utf-8") as handle:
            handle.write(content)

    def write_workflow(self, command, path=".github/workflows/verify.yaml"):
        """A workflow whose single step runs the given command, on line 4.

        The scaffold is the same in every one of these, so the tests name only
        the flag they are about.
        """
        self.write(path, "jobs:\n  build:\n    steps:\n"
                         "      - run: {0}\n".format(command))

    def write_root(self, value=GOOD_VALUE, raw_property=None, profiles="",
                   extensions=EXTENSIONS):
        if raw_property is None:
            raw_property = PROPERTY_LINE.format(value)
        self.write("pom.xml", ROOT_POM.format(property=raw_property,
                                              profiles=profiles,
                                              extensions=extensions))

    def write_consumer(self, shared=None, executions=None,
                       path="java-sdk/client/pom.xml"):
        if shared is None:
            shared = SHARED_FOLDER.format(EXPRESSION)
        if executions is None:
            executions = execution("default")
        self.write(path, CONSUMER_POM.format(shared=shared,
                                             executions=executions))

    def run_guard(self):
        """The exit code, stderr, and stdout of the check."""
        errors, output = io.StringIO(), io.StringIO()
        with contextlib.redirect_stderr(errors), \
                contextlib.redirect_stdout(output):
            code = guard.main()
        return code, errors.getvalue(), output.getvalue()

    def assertAccepted(self):
        code, reported, output = self.run_guard()
        self.assertEqual(0, code, "expected this tree to pass:\n" + reported)
        # A guard that returns 0 without checking anything passes every accepted
        # case here, so the success line has to name the value it checked.
        self.assertIn(GOOD_VALUE, output)

    def assertRejected(self, *expected):
        code, reported, _ = self.run_guard()
        self.assertEqual(1, code, "expected this tree to be rejected")
        for fragment in expected:
            self.assertIn(fragment, reported)

    # ---------------- accepted ----------------

    def test_the_real_shape_is_accepted(self):
        self.assertAccepted()

    def test_the_folder_set_on_the_execution_is_accepted(self):
        """java-sdk/client-v2 sets it here rather than on the plugin."""
        self.write_consumer(shared="", executions=execution("v2", EXPRESSION))
        self.assertAccepted()

    def test_a_plugin_without_executions_is_accepted(self):
        """The default lifecycle binding still picks up plugin configuration."""
        self.write_consumer(executions="")
        self.assertAccepted()

    def test_property_sharing_its_line_with_a_comment_is_accepted(self):
        """A line-oriented comment strip dropped this line whole, tag included."""
        self.write_root(raw_property=PROPERTY_LINE.format(GOOD_VALUE).rstrip("\n")
                        + " <!-- kept out of target/ so\n"
                          "         mvn clean spares it -->\n")
        self.assertAccepted()

    def test_property_after_a_comment_close_is_accepted(self):
        """Live XML following --> on the same line used to be discarded."""
        self.write_root(raw_property="    <!-- see DEVELOPING.md\n      --> "
                        + PROPERTY_LINE.format(GOOD_VALUE).strip() + "\n")
        self.assertAccepted()

    def test_a_padded_value_is_accepted(self):
        """Maven trims the text of a property, so this is the same value."""
        self.write_root(raw_property="    <kiota.binary.folder>\n      {0}\n"
                        "    </kiota.binary.folder>\n".format(GOOD_VALUE))
        self.assertAccepted()

    def test_prose_naming_the_flag_is_accepted(self):
        """DEVELOPING.md documents the flag, and a doc cannot run Maven."""
        self.write(".github/workflows/README.md",
                   "Pass -Dkiota.binary.folder to move the binary.\n")
        self.assertAccepted()

    def test_a_commented_out_flag_in_a_workflow_is_accepted(self):
        self.write(".github/workflows/build.yaml",
                   "jobs:\n  build:\n    steps:\n"
                   "      # never pass -Dkiota.binary.folder here\n"
                   "      - run: ./mvnw install\n")
        self.assertAccepted()

    def test_a_python_file_is_not_scanned(self):
        """Why this file may write the flags out rather than assemble them.

        An earlier version scanned every file under .github, so its own source
        reported itself. Assembling the flags fixed the source and not the
        bytecode, where the compiler folds the two literals back into one.
        """
        self.write(".github/scripts/sample.py",
                   'FLAG = "-Dkiota.binary.folder=/tmp/elsewhere"\n')
        self.assertAccepted()

    def test_a_dangling_symlink_is_tolerated(self):
        """os.walk lists one as a file, and opening it raises FileNotFoundError."""
        os.makedirs(".github/workflows")
        os.symlink("/nonexistent/target", ".github/workflows/stale.yaml")
        self.assertAccepted()

    def test_a_longer_resolver_property_is_accepted(self):
        """maven.repo.local.tail.threads is a real property that moves nothing.

        A word boundary after "local" matches it and reports a flag that does
        not send the binary anywhere.
        """
        self.write_workflow("./mvnw -Dmaven.repo.local.tail.threads=4 install")
        self.assertAccepted()

    # ---------------- rejected: the property ----------------

    def test_folder_under_target_is_rejected(self):
        """The regression this check exists for."""
        self.write_root("${session.executionRootDirectory}/target/kiota-binary")
        self.assertRejected("expected " + GOOD_VALUE)

    def test_an_artifact_shaped_path_is_rejected(self):
        """io/kiota/... has the shape of a groupId tree that really exists."""
        self.write_root("${settings.localRepository}/io/kiota")
        self.assertRejected("expected " + GOOD_VALUE)

    def test_the_bare_repository_root_is_rejected(self):
        self.write_root("${settings.localRepository}")
        self.assertRejected("expected " + GOOD_VALUE)

    def test_missing_property_is_rejected(self):
        self.write_root(raw_property="")
        self.assertRejected("declares no <kiota.binary.folder>")

    def test_a_pom_without_properties_is_rejected(self):
        self.write("pom.xml", MODULE_POM.format(body=""))
        self.assertRejected("declares no <kiota.binary.folder>")

    def test_commented_out_property_is_rejected(self):
        """Commented out reads as present to grep, and as absent to a parser."""
        self.write_root(raw_property="    <!--\n"
                        + PROPERTY_LINE.format(GOOD_VALUE) + "    -->\n")
        self.assertRejected("declares no <kiota.binary.folder>")

    def test_a_repeated_property_is_rejected(self):
        """Maven keeps the last one and does not warn."""
        self.write_root(raw_property=PROPERTY_LINE.format(GOOD_VALUE)
                        + PROPERTY_LINE.format("/tmp/elsewhere"))
        self.assertRejected("declares <kiota.binary.folder> 2 times")

    def test_redefining_the_local_repository_is_rejected(self):
        """It beats the real path, and only ~/.m2/repository is cached."""
        self.write_root(raw_property="    <settings.localRepository>/tmp/repo"
                        "</settings.localRepository>\n"
                        + PROPERTY_LINE.format(GOOD_VALUE))
        self.assertRejected("declares <settings.localRepository>")

    def test_a_profile_overriding_the_property_is_rejected(self):
        """It leaves the declaration above it untouched and still wins."""
        self.write_root(profiles="  <profiles><profile><id>ci</id><properties>"
                        "<kiota.binary.folder>/tmp/elsewhere"
                        "</kiota.binary.folder></properties></profile>"
                        "</profiles>\n")
        self.assertRejected("Profile ci in pom.xml")

    def test_an_empty_property_is_rejected(self):
        """An empty value resolves to the plugin's own default folder."""
        self.write_root(raw_property="    <kiota.binary.folder/>\n")
        self.assertRejected("is empty, expected " + GOOD_VALUE)

    def test_dropping_the_os_maven_plugin_extension_is_rejected(self):
        """Without it the classifier is never substituted, and nothing else notices.

        Maven passes ${os.detected.classifier} through as text, so the plugin
        creates a directory of that literal name and every other check here
        stays green.
        """
        self.write_root(extensions="")
        self.assertRejected("registers no os-maven-plugin build extension")

    # ---------------- rejected: other poms ----------------

    def test_a_module_redeclaring_the_property_is_rejected(self):
        """The root pom stays correct and the module ignores it anyway."""
        self.write("java-sdk/client/other/pom.xml",
                   MODULE_POM.format(body="  <properties>\n"
                                     + PROPERTY_LINE.format("/tmp/elsewhere")
                                     + "  </properties>\n"))
        self.assertRejected("redeclares <kiota.binary.folder>")

    def test_a_module_profile_overriding_the_property_is_rejected(self):
        self.write("app/pom.xml",
                   MODULE_POM.format(body="  <profiles><profile><id>fast</id>"
                                     "<properties><kiota.binary.folder>/tmp/x"
                                     "</kiota.binary.folder></properties>"
                                     "</profile></profiles>\n"))
        self.assertRejected("Profile fast in app/pom.xml")

    def test_a_module_relocating_the_repository_is_rejected(self):
        self.write("app/pom.xml",
                   MODULE_POM.format(body="  <properties>"
                                     "<settings.localRepository>/tmp/repo"
                                     "</settings.localRepository></properties>\n"))
        self.assertRejected("app/pom.xml declares <settings.localRepository>")

    def test_a_profile_moving_only_the_repository_names_that_property(self):
        """The message has to name the lever that was pulled, not the other one."""
        self.write("app/pom.xml",
                   MODULE_POM.format(body="  <profiles><profile><id>ci</id>"
                                     "<properties><settings.localRepository>"
                                     "/tmp/repo</settings.localRepository>"
                                     "</properties></profile></profiles>\n"))
        self.assertRejected("Profile ci in app/pom.xml overrides "
                            "<settings.localRepository>")

    def test_a_malformed_pom_does_not_stop_the_scan(self):
        """A broken file reports itself and the scan carries on past it.

        java-sdk/a sorts before java-sdk/client, so a check that stopped at the
        first parse error would never reach the consumer below it.
        """
        self.write("java-sdk/a/pom.xml", "<project><artifactId>x</project>")
        self.write_consumer(shared="")
        self.assertRejected("java-sdk/a/pom.xml is not valid XML",
                            "sets no <targetBinaryFolder>")

    # ---------------- rejected: committed settings ----------------

    def test_a_settings_file_moving_the_repository_is_rejected(self):
        """.github/ci-settings.xml is passed with -s by every CI build."""
        self.write(".github/ci-settings.xml",
                   '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">\n'
                   "  <localRepository>/tmp/repo</localRepository>\n"
                   "</settings>\n")
        self.assertRejected("names its own <localRepository>")

    def test_a_settings_profile_setting_the_property_is_rejected(self):
        """A settings profile outranks the pom."""
        self.write(".github/ci-settings.xml",
                   '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">\n'
                   "  <profiles><profile><id>ci</id><properties>"
                   "<kiota.binary.folder>/tmp/x</kiota.binary.folder>"
                   "</properties></profile></profiles>\n</settings>\n")
        self.assertRejected("Profile ci in .github/ci-settings.xml")

    def test_a_non_settings_xml_under_github_is_ignored(self):
        """.github holds other XML, and only a <settings> root is checked."""
        self.write(".github/dependabot-template.xml",
                   "<config><localRepository>/tmp/repo</localRepository></config>\n")
        self.assertAccepted()

    # ---------------- rejected: the consumers ----------------

    def test_a_hardcoded_folder_is_rejected(self):
        """One line in the file a developer is already in, and green elsewhere."""
        self.write_consumer(
            shared=SHARED_FOLDER.format("${project.build.directory}/kiota-binary"))
        self.assertRejected("rather than " + EXPRESSION)

    def test_an_execution_without_the_folder_is_rejected(self):
        """The check is per execution: the first one must not cover the second."""
        self.write_consumer(shared="",
                            executions=execution("v2", EXPRESSION)
                            + execution("v3"))
        self.assertRejected("execution v3", "sets no <targetBinaryFolder>")

    def test_an_id_less_execution_is_named_default(self):
        """java-sdk/client has this shape, and Maven calls that id default."""
        self.write_consumer(
            shared="",
            executions="      <execution><goals><goal>generate</goal></goals>"
                       "</execution>\n")
        self.assertRejected("execution default", "sets no <targetBinaryFolder>")

    def test_a_module_dropping_the_folder_is_rejected(self):
        """A second module still carrying it must not cover this one."""
        self.write_consumer(shared="", path="java-sdk/client-v2/pom.xml")
        self.assertRejected("java-sdk/client-v2/pom.xml")

    def test_no_consumer_at_all_is_rejected(self):
        """Either the plugin moved, or this check is looking in the wrong place."""
        os.remove("java-sdk/client/pom.xml")
        self.assertRejected("No pom configures kiota-maven-plugin")

    def test_a_malformed_consumer_pom_is_rejected(self):
        self.write("java-sdk/client/pom.xml", "<project><artifactId>x</project>")
        self.assertRejected("java-sdk/client/pom.xml is not valid XML")

    def test_a_dangling_pom_symlink_is_reported_not_crashed(self):
        """os.walk lists one as a file, and ET.parse raises OSError on it.

        A lint step that dies with a traceback says nothing about what to fix,
        so every parse goes through the same reporting path.
        """
        os.makedirs("mod")
        os.symlink("/nonexistent/target", "mod/pom.xml")
        self.assertRejected("Could not read mod/pom.xml")

    def test_a_dangling_settings_symlink_is_reported_not_crashed(self):
        os.makedirs(".github")
        os.symlink("/nonexistent/target", ".github/ci-settings.xml")
        self.assertRejected("Could not read .github/ci-settings.xml")

    def test_a_dangling_root_pom_symlink_is_reported_not_crashed(self):
        os.remove("pom.xml")
        os.symlink("/nonexistent/target", "pom.xml")
        self.assertRejected("Could not read pom.xml")

    # ---------------- rejected: the command line ----------------

    def test_maven_config_override_is_rejected(self):
        self.write(".mvn/maven.config",
                   "-T 1C\n-Dkiota.binary.folder=target/kiota-binary\n")
        self.assertRejected(".mvn/maven.config:2")

    def test_a_flag_in_jvm_config_is_rejected(self):
        """The .mvn scope is every *.config there, not maven.config alone."""
        self.write(".mvn/jvm.config", "-Dkiota.binary.folder=/tmp/k\n")
        self.assertRejected("jvm.config:1")

    def test_the_wrapper_script_is_scanned(self):
        """mvnw has no extension and reaches every build the repository starts."""
        self.write("mvnw", "#!/bin/sh\nexec mvn -Dkiota.binary.folder=/tmp/k \"$@\"\n")
        self.assertRejected("mvnw:2")

    def test_the_windows_wrapper_script_is_scanned(self):
        """mvnw.cmd runs the same builds with a different extension."""
        self.write("mvnw.cmd", "@echo off\nmvn -Dkiota.binary.folder=/tmp/k %*\n")
        self.assertRejected("mvnw.cmd:2")

    def test_an_unreadable_scanned_file_is_reported_not_crashed(self):
        """A permission error is a finding, not a traceback from a lint step."""
        self.write("scripts/build.sh", "./mvnw install\n")
        os.chmod("scripts/build.sh", 0)
        if os.access("scripts/build.sh", os.R_OK):
            self.skipTest("running as a user that ignores file permissions")
        self.assertRejected("Could not read scripts/build.sh")

    def test_a_commented_out_maven_config_line_is_accepted(self):
        """A leading # is the only comment Maven honours in maven.config.

        Checked against Maven 3.9.8, the version the wrapper pins: it reads each
        line as one whole argument rather than splitting on whitespace, so a #
        anywhere else is part of the value. Only the leading form disarms the
        flag, and only that form is treated as a comment here.
        """
        self.write(".mvn/maven.config",
                   "-T 1C\n# -Dkiota.binary.folder=target/kiota-binary\n")
        self.assertAccepted()

    def test_every_spelling_of_the_flag_is_rejected(self):
        """Maven accepts all four, and a joined -D needle matches only the first.

        --define=x=y is the commons-cli spelling, which a match requiring a
        space after the option name misses.
        """
        for flag in ("-Dkiota.binary.folder=/tmp/k",
                     "-D kiota.binary.folder=/tmp/k",
                     "--define kiota.binary.folder=/tmp/k",
                     "--define=kiota.binary.folder=/tmp/k"):
            with self.subTest(flag=flag):
                self.write_workflow("./mvnw {0} install".format(flag))
                self.assertRejected("verify.yaml:4")

    def test_the_quoted_property_name_is_rejected(self):
        """The shell strips the quotes before Maven sees the argument."""
        self.write("scripts/build.sh",
                   './mvnw -D"kiota.binary.folder"=/tmp/k install\n')
        self.assertRejected("build.sh:1")

    def test_a_makefile_moving_the_folder_is_rejected(self):
        """operator/Makefile really does run mvn clean install and mvn verify."""
        self.write("operator/Makefile",
                   "build:\n\tmvn clean install -Dkiota.binary.folder=/tmp/k\n")
        self.assertRejected("Makefile:2")

    def test_a_dockerfile_moving_the_folder_is_rejected(self):
        self.write("console-plugin/Dockerfile",
                   "FROM maven\nRUN mvn package -Dkiota.binary.folder=/tmp/k\n")
        self.assertRejected("Dockerfile:2")

    def test_an_override_after_a_quoted_hash_is_rejected(self):
        """A # inside quotes is not a comment opener.

        Cutting the line there would discard the build command that follows and
        report the file as clean.
        """
        self.write_workflow('echo "see #10213" && '
                            "./mvnw -Dkiota.binary.folder=/tmp/k install")
        self.assertRejected("verify.yaml:4")

    def test_an_apostrophe_does_not_swallow_a_real_comment(self):
        """An unpaired quote is prose, not a string, so the # still opens a comment.

        The line has to carry something before the apostrophe. With a leading #
        the scan returns at index zero and never reaches the quote, so the test
        would pass with the partner check deleted and pin nothing.
        """
        self.write("scripts/build.sh",
                   "echo it's fine # -Dkiota.binary.folder=/tmp/k\n")
        self.assertAccepted()

    def test_relocating_the_repository_is_rejected(self):
        """setup-maven-cache saves ~/.m2/repository and nothing else."""
        self.write(".github/actions/setup-maven-cache/action.yaml",
                   "runs:\n  steps:\n"
                   '    - run: echo "MAVEN_ARGS=-Dmaven.repo.local=$RUNNER_TEMP/m2"'
                   ' >> "$GITHUB_ENV"\n')
        self.assertRejected("action.yaml:3")

    def test_an_override_in_a_shell_script_is_rejected(self):
        """Scoping by role rather than by directory is what reaches scripts/."""
        self.write("scripts/build.sh",
                   "#!/bin/bash\n./mvnw -Dkiota.binary.folder=/tmp/k install\n")
        self.assertRejected("scripts/build.sh:2")

    # ---------------- rejected: the tree itself ----------------

    def test_malformed_root_pom_is_rejected(self):
        self.write("pom.xml", "<project><artifactId>x</project>")
        self.assertRejected("pom.xml is not valid XML")

    def test_missing_root_pom_is_rejected(self):
        os.remove("pom.xml")
        self.assertRejected("Run this from the repository root")


if __name__ == "__main__":
    unittest.main()
