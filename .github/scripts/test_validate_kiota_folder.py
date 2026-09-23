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
The accepted cases carry as much weight as the rejected ones: an earlier version
of this check matched text rather than parsing, and it rejected a property whose
line also opened a comment while accepting one that was commented out.
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

# The script's name has a hyphen, so a plain import cannot reach it and it is
# loaded from its path instead, the same shape test_parse_flaky_tests uses. The
# None check is for the type checker: both are declared optional, and a script
# that really moved raises FileNotFoundError from exec_module first.
spec = importlib.util.spec_from_file_location("validate_kiota_folder", script_path)
if spec is None or spec.loader is None:
    raise ImportError("Could not load {0}".format(script_path))
guard = importlib.util.module_from_spec(spec)
sys.modules["validate_kiota_folder"] = guard
spec.loader.exec_module(guard)

# Read from the script rather than restated here. A second copy of the expected
# value would let the two drift, and these tests would keep passing against
# whatever the script had been changed to.
GOOD_VALUE = guard.EXPECTED
EXPRESSION = guard.CONSUMER_EXPRESSION

# The namespace matters: the script matches elements with {*} wildcards, so a
# fixture declaring none would still parse and still pass, and would stop
# representing a real pom without any test noticing.
PREAMBLE = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
"""

ROOT_POM = PREAMBLE + """  <artifactId>apicurio-registry</artifactId>
  <properties>
{property}  </properties>
{profiles}  <build>{extensions}</build>
</project>
"""

EXTENSIONS = ("<extensions><extension><groupId>kr.motd.maven</groupId>"
              "<artifactId>os-maven-plugin</artifactId></extension></extensions>")

PROPERTY_LINE = "    <kiota.binary.folder>{0}</kiota.binary.folder>\n"

PROFILE = ("  <profiles><profile><id>ci</id><properties>"
           "<{0}>/tmp/elsewhere</{0}></properties></profile></profiles>\n")

# java-sdk/client sets the folder on the plugin and java-sdk/client-v2 sets it on
# the execution. Maven merges plugin configuration into every execution, so both
# shapes are correct and both are exercised below.
CONSUMER_POM = PREAMBLE + """  <artifactId>apicurio-registry-java-sdk</artifactId>
{properties}{profiles}  <build><plugins><plugin>
    <groupId>io.kiota</groupId>
    <artifactId>kiota-maven-plugin</artifactId>
{shared}    <executions>
{executions}    </executions>
  </plugin></plugins></build>
</project>
"""

FOLDER = "<configuration><targetBinaryFolder>{0}</targetBinaryFolder></configuration>"
SHARED_FOLDER = "    " + FOLDER + "\n"
EXECUTION = "      <execution>{0}<goals><goal>generate</goal></goals>" \
            "{1}</execution>\n"


def execution(name, folder=None):
    """One <execution>, id-less when name is None.

    An absent folder and an empty one are different trees to the check: pass
    None to omit the element and "" to build an empty one.
    """
    identifier = "" if name is None else "<id>{0}</id>".format(name)
    own = "" if folder is None else FOLDER.format(folder)
    return EXECUTION.format(identifier, own)


class KiotaFolderCheckTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        previous = os.getcwd()
        os.chdir(self.tmp.name)
        self.addCleanup(os.chdir, previous)
        self.write_root()
        self.write_consumer()
        # Both consumers, because the check names the set it expects rather than
        # counting matches. A fixture with one of them would never exercise the
        # half-match the real tree can reach.
        self.write_consumer(shared="", executions=execution("v2", EXPRESSION),
                            path="java-sdk/client-v2/pom.xml")

    def write(self, relative_path, content):
        full = os.path.join(self.tmp.name, relative_path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as handle:
            handle.write(content)

    def write_workflow(self, command, path=".github/workflows/verify.yaml"):
        """A workflow whose single step runs the given command, on line 4."""
        self.write(path, "jobs:\n  build:\n    steps:\n"
                         "      - run: {0}\n".format(command))

    def write_root(self, value=GOOD_VALUE, raw_property=None, profiles="",
                   extensions=EXTENSIONS):
        if raw_property is None:
            raw_property = PROPERTY_LINE.format(value)
        self.write("pom.xml", ROOT_POM.format(property=raw_property,
                                              profiles=profiles,
                                              extensions=extensions))

    def write_consumer(self, shared=None, executions=None, properties="",
                       profiles="", path="java-sdk/client/pom.xml"):
        if shared is None:
            shared = SHARED_FOLDER.format(EXPRESSION)
        if executions is None:
            executions = execution("default")
        self.write(path, CONSUMER_POM.format(shared=shared, executions=executions,
                                             properties=properties,
                                             profiles=profiles))

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
        # A guard returning 0 without checking anything passes every accepted
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

    def test_a_readme_beside_the_workflows_is_not_scanned(self):
        """Only .yml and .yaml are read, and a doc cannot run Maven."""
        self.write(".github/workflows/README.md",
                   "Pass -Dkiota.binary.folder to move the binary.\n")
        self.assertAccepted()

    def test_a_commented_out_flag_in_a_workflow_is_accepted(self):
        self.write(".github/workflows/build.yaml",
                   "jobs:\n  build:\n    steps:\n"
                   "      # never pass -Dkiota.binary.folder here\n"
                   "      - run: ./mvnw install\n")
        self.assertAccepted()

    def test_a_script_outside_the_workflows_is_not_scanned(self):
        """The scan is the workflow directory, and this pins that boundary.

        A shell script moving the folder is a real way to do it and deliberately
        out of scope: it moves the folder for one developer rather than for the
        pipeline. The pom comment carries the full list of what is left out.
        """
        self.write("scripts/build.sh",
                   "#!/bin/bash\n./mvnw -Dkiota.binary.folder=/tmp/k install\n")
        self.assertAccepted()

    def test_a_longer_resolver_property_is_accepted(self):
        """maven.repo.local.tail.threads is a real property that moves nothing.

        A word boundary after "local" matches it and reports a flag that sends
        the binary nowhere.
        """
        self.write_workflow("./mvnw -Dmaven.repo.local.tail.threads=4 install")
        self.assertAccepted()

    def test_a_similarly_named_property_is_not_reported(self):
        """The trailing guard excludes the hyphen, and folder-x moves nothing.

        A word boundary ends at the hyphen, and reporting this flag would fail
        an unrelated PR on a merge-blocking step.
        """
        self.write_workflow("./mvnw -Dkiota.binary.folder-x=1 install")
        self.assertAccepted()

    # ---------------- rejected: the root pom ----------------

    def test_a_different_value_is_rejected(self):
        """The three near misses a looser test would let through.

        target/ is the regression this check exists for, io/kiota is the
        artifact-shaped path the pom comment exists to avoid, and the bare
        repository root is what a prefix test accepts.
        """
        for value in ("${session.executionRootDirectory}/target/kiota-binary",
                      "${settings.localRepository}/io/kiota",
                      "${settings.localRepository}"):
            with self.subTest(value=value):
                self.write_root(value)
                self.assertRejected("expected " + GOOD_VALUE)

    def test_missing_property_is_rejected(self):
        self.write_root(raw_property="")
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

    def test_an_empty_property_is_rejected(self):
        """An empty value resolves to the plugin's own default folder."""
        self.write_root(raw_property="    <kiota.binary.folder/>\n")
        self.assertRejected("is empty, expected " + GOOD_VALUE)

    def test_redefining_the_local_repository_is_rejected(self):
        """It beats the real path, and only ~/.m2/repository is cached."""
        self.write_root(raw_property="    <settings.localRepository>/tmp/repo"
                        "</settings.localRepository>\n"
                        + PROPERTY_LINE.format(GOOD_VALUE))
        self.assertRejected("declares <settings.localRepository>")

    def test_a_profile_is_rejected_for_either_property(self):
        """A profile leaves the declaration above it untouched and still wins.

        Both levers are checked, and the message names the one that was pulled
        rather than the other.
        """
        for moved in ("kiota.binary.folder", "settings.localRepository"):
            with self.subTest(property=moved):
                self.write_root(profiles=PROFILE.format(moved))
                self.assertRejected("Profile ci in pom.xml overrides <{0}>"
                                    .format(moved))

    def test_a_profile_with_no_id_is_named_in_the_message(self):
        """findtext's fallback, so the finding still names what was pulled."""
        self.write_root(profiles="  <profiles><profile><properties>"
                        "<kiota.binary.folder>/tmp/elsewhere"
                        "</kiota.binary.folder></properties></profile>"
                        "</profiles>\n")
        self.assertRejected("Profile with no id in pom.xml")

    def test_dropping_the_os_maven_plugin_extension_is_rejected(self):
        """Maven then passes ${os.detected.classifier} through as text.

        The plugin creates a directory of that literal name, and every other
        check here stays green.
        """
        self.write_root(extensions="")
        self.assertRejected("registers no os-maven-plugin build extension")

    # ---------------- rejected: the consumers ----------------

    def test_a_hardcoded_folder_is_rejected(self):
        """One line in the file a developer is already in, and green elsewhere."""
        self.write_consumer(
            shared=SHARED_FOLDER.format("${project.build.directory}/kiota-binary"))
        self.assertRejected("rather than " + EXPRESSION)

    def test_an_empty_folder_on_an_execution_is_rejected(self):
        """Empty is not absent, so the message names the empty value."""
        self.write_consumer(shared="", executions=execution("v2", ""))
        self.assertRejected("sets <targetBinaryFolder> to empty")

    def test_an_execution_without_the_folder_is_rejected(self):
        """The check is per execution: the first one must not cover the second."""
        self.write_consumer(shared="", executions=execution("v2", EXPRESSION)
                            + execution("v3"))
        self.assertRejected("execution v3", "sets no <targetBinaryFolder>")

    def test_an_id_less_execution_is_named_default(self):
        """java-sdk/client has this shape, and Maven calls that id default."""
        self.write_consumer(
            shared="",
            executions="      <execution><goals><goal>generate</goal></goals>"
                       "</execution>\n")
        self.assertRejected("execution default", "sets no <targetBinaryFolder>")

    def test_a_plugin_with_no_executions_names_the_declaration(self):
        """Nothing here is an execution, so the message must not invent an id."""
        self.write_consumer(shared="", executions="")
        self.assertRejected("the plugin declaration",
                            "sets no <targetBinaryFolder>")

    def test_a_consumer_redeclaring_the_property_is_rejected(self):
        """The root pom stays correct and the module ignores it anyway."""
        self.write_consumer(properties="  <properties>\n"
                            + PROPERTY_LINE.format("/tmp/elsewhere")
                            + "  </properties>\n")
        self.assertRejected("redeclares <kiota.binary.folder>")

    def test_a_profile_in_a_consumer_is_rejected_for_either_property(self):
        """The root pom's profile check, on the module that interpolates it.

        The inherited value keeps reading as expected everywhere else, and the
        profile still wins inside the module whenever it is active.
        """
        for moved in ("kiota.binary.folder", "settings.localRepository"):
            with self.subTest(property=moved):
                self.write_consumer(profiles=PROFILE.format(moved))
                self.assertRejected("Profile ci in java-sdk/client/pom.xml "
                                    "overrides <{0}>".format(moved))

    def test_a_module_dropping_the_folder_is_rejected(self):
        """A second module still carrying it must not cover this one."""
        self.write_consumer(shared="", path="java-sdk/client-v2/pom.xml")
        self.assertRejected("java-sdk/client-v2/pom.xml")

    def test_a_missing_consumer_is_rejected(self):
        """Every named pom is read, so one going away is a finding, not a pass."""
        os.remove("java-sdk/client/pom.xml")
        self.assertRejected("Could not read java-sdk/client/pom.xml")

    def test_one_consumer_dropping_the_plugin_is_rejected(self):
        """The half-match a boolean cannot see.

        With client still declaring the plugin, a flag set by the first match
        reports nothing and the check prints ok. Naming the expected set is what
        makes the missing declaration visible.
        """
        self.write("java-sdk/client-v2/pom.xml",
                   PREAMBLE + "  <artifactId>module</artifactId>\n</project>\n")
        self.assertRejected("No plugin declaration of kiota-maven-plugin in "
                            "java-sdk/client-v2/pom.xml")

    def test_a_malformed_consumer_pom_is_reported_not_crashed(self):
        """A lint step dying with a traceback says nothing about what to fix."""
        self.write("java-sdk/client/pom.xml", "<project><artifactId>x</project>")
        self.assertRejected("java-sdk/client/pom.xml is not valid XML")

    # ---------------- rejected: the command line ----------------

    def test_every_spelling_of_the_flag_is_rejected(self):
        """Maven accepts all of them, and a joined -D needle matches only the first.

        --define=x=y is the commons-cli spelling, which a match requiring a
        space after the option name misses. The two-space forms are here because
        matching the gap inside one alternative covered -D and not --define, so
        the guard was asymmetric between two spellings of the same flag. The
        shell strips the quotes in the last one before Maven sees the argument.
        """
        for flag in ("-Dkiota.binary.folder=/tmp/k",
                     "-D kiota.binary.folder=/tmp/k",
                     "-D  kiota.binary.folder=/tmp/k",
                     "--define kiota.binary.folder=/tmp/k",
                     "--define  kiota.binary.folder=/tmp/k",
                     "--define=kiota.binary.folder=/tmp/k",
                     '-D"kiota.binary.folder"=/tmp/k'):
            with self.subTest(flag=flag):
                self.write_workflow("./mvnw {0} install".format(flag))
                self.assertRejected("verify.yaml:4")

    def test_a_valueless_flag_is_rejected(self):
        """A valueless -D sets the property to the literal "true".

        Probed on Maven 3.9.8 with help:evaluate, so requiring "=" in the
        pattern would open exactly this hole.
        """
        self.write_workflow("./mvnw -Dkiota.binary.folder install")
        self.assertRejected("verify.yaml:4")

    def test_every_property_that_moves_the_folder_is_rejected(self):
        """All three were probed against the real pom with help:evaluate.

        maven.repo.local relocates the whole repository, and only
        ~/.m2/repository is cached. settings.localRepository is the anchor the
        expected value is built on, so -Dsettings.localRepository=/tmp/x
        resolves kiota.binary.folder to /tmp/x/.cache/kiota-binary/linux-x86_64.
        The pom side of the anchor is checked above, and the command line
        outranks it.
        """
        for moved in ("kiota.binary.folder", "maven.repo.local",
                      "settings.localRepository"):
            with self.subTest(property=moved):
                self.write_workflow("./mvnw -D{0}=/tmp/x install".format(moved))
                self.assertRejected("verify.yaml:4", moved)

    def test_a_yml_workflow_is_scanned_too(self):
        """Both spellings are live on GitHub, and this tree uses .yaml."""
        self.write_workflow("./mvnw -Dkiota.binary.folder=/tmp/k install",
                            path=".github/workflows/legacy.yml")
        self.assertRejected("legacy.yml:4")

    def test_an_override_after_a_mid_token_hash_is_rejected(self):
        """Only a # that starts a token opens a comment in YAML.

        Cutting the line at any # would discard the build command that follows
        and report the file as clean. A # inside quotes is not covered: tracking
        that cost more than the case is worth, and no step here has that shape.
        """
        self.write_workflow("./mvnw -Dtag=v1#2 "
                            "-Dkiota.binary.folder=/tmp/k install")
        self.assertRejected("verify.yaml:4")

    def test_a_dangling_workflow_symlink_is_reported_not_crashed(self):
        """listdir names one, and opening it raises FileNotFoundError."""
        os.makedirs(".github/workflows")
        os.symlink("/nonexistent/target", ".github/workflows/stale.yaml")
        self.assertRejected("Could not read .github/workflows/stale.yaml")

    # ---------------- rejected: the tree itself ----------------

    def test_malformed_root_pom_is_rejected(self):
        self.write("pom.xml", "<project><artifactId>x</project>")
        self.assertRejected("pom.xml is not valid XML")

    def test_missing_root_pom_is_rejected(self):
        os.remove("pom.xml")
        self.assertRejected("Run this from the repository root")


if __name__ == "__main__":
    unittest.main()
