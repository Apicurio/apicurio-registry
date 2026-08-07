#!/usr/bin/env python3
"""Validate inter-module test dependencies across the Maven reactor.

Parallel builds (-T) only order two modules relative to each other if the
reactor knows they are connected. Test classes are shared between modules
through a test-jar, and that link exists solely because the consumer declares
the dependency: nothing about `import`ing a class from another module's
src/test/java creates a reactor edge on its own.

When the declaration is missing the build is a coin flip. With enough threads
the consumer reaches test-compile before the producer has attached its
test-jar and the compiler reports the shared class as a missing symbol; with
fewer threads the modules happen to build in order and everything passes. The
quieter failure is worse: if an earlier build left a stale test-jar in the
local repository, the consumer compiles against that instead and the mismatch
only surfaces later, somewhere unrelated.

Two invariants are checked here, both cheap enough to run before the build:

  1. A module whose test sources use another module's test classes declares an
     explicit test-jar dependency on it.
  2. Every module named by such a dependency actually produces a test-jar at
     the package phase. Binding maven-jar-plugin's test-jar goal to any other
     phase leaves the artifact outside the module's normal output, so the
     reactor cannot guarantee it exists when a consumer asks for it.

The repository is located relative to this file, so the working directory does
not matter. Every violation is printed; the exit status is non-zero if there
was at least one.
"""

import os
import re
import sys
import xml.etree.ElementTree as ElementTree

POM_NS = "{http://maven.apache.org/POM/4.0.0}"

# Directories that never hold reactor sources but are expensive to walk.
PRUNED_DIRS = {".git", "node_modules", "target"}

IMPORT_PATTERN = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+(?:\.\*)?)\s*;", re.MULTILINE)


def find_poms(root):
    poms = []
    for directory, subdirectories, files in os.walk(root):
        subdirectories[:] = [d for d in subdirectories if d not in PRUNED_DIRS]
        if "pom.xml" in files:
            poms.append(os.path.join(directory, "pom.xml"))
    return sorted(poms)


def declared_dependencies(project):
    """Dependencies the module really gets, i.e. excluding dependencyManagement.

    Profile dependencies count: a module that only shares test classes under a
    profile still needs the reactor edge whenever that profile is active.
    """
    blocks = []
    direct = project.find(POM_NS + "dependencies")
    if direct is not None:
        blocks.append(direct)
    profiles = project.find(POM_NS + "profiles")
    if profiles is not None:
        for profile in profiles.findall(POM_NS + "profile"):
            block = profile.find(POM_NS + "dependencies")
            if block is not None:
                blocks.append(block)
    return [dependency for block in blocks for dependency in block.findall(POM_NS + "dependency")]


def test_jar_dependencies(project):
    """Artifact ids this module consumes as a test-jar.

    Both spellings resolve to the same artifact and both create the reactor
    edge, so both are accepted: <type>test-jar</type>, and the older
    <classifier>tests</classifier> on a plain jar dependency.
    """
    consumed = set()
    for dependency in declared_dependencies(project):
        artifact_type = dependency.findtext(POM_NS + "type")
        classifier = dependency.findtext(POM_NS + "classifier")
        if artifact_type == "test-jar" or (artifact_type is None and classifier == "tests"):
            consumed.add(dependency.findtext(POM_NS + "artifactId"))
    return consumed


def test_jar_execution_phases(project):
    """Phases the maven-jar-plugin test-jar goal is bound to, if any.

    An empty string stands for "no explicit phase", which is the goal's own
    default of package.
    """
    phases = []
    build = project.find(POM_NS + "build")
    if build is None:
        return phases
    plugins = build.find(POM_NS + "plugins")
    if plugins is None:
        return phases
    for plugin in plugins.findall(POM_NS + "plugin"):
        if plugin.findtext(POM_NS + "artifactId") != "maven-jar-plugin":
            continue
        executions = plugin.find(POM_NS + "executions")
        if executions is None:
            continue
        for execution in executions.findall(POM_NS + "execution"):
            goals = execution.find(POM_NS + "goals")
            if goals is None:
                continue
            if any(goal.text == "test-jar" for goal in goals.findall(POM_NS + "goal")):
                phases.append(execution.findtext(POM_NS + "phase") or "")
    return phases


def java_types(module_dir, source_set):
    """Fully qualified names of the Java types in one source set of a module."""
    source_root = os.path.join(module_dir, "src", source_set, "java")
    types = {}
    for directory, subdirectories, files in os.walk(source_root):
        subdirectories[:] = [d for d in subdirectories if d not in PRUNED_DIRS]
        for name in files:
            if name.endswith(".java"):
                path = os.path.join(directory, name)
                relative = os.path.relpath(path, source_root)
                types[relative[: -len(".java")].replace(os.sep, ".")] = path
    return types


def package_of(qualified_name):
    return qualified_name.rpartition(".")[0]


def simple_name_of(qualified_name):
    return qualified_name.rpartition(".")[2]


class Module:
    def __init__(self, pom_path, project):
        self.pom_path = pom_path
        self.directory = os.path.dirname(pom_path)
        self.artifact_id = project.findtext(POM_NS + "artifactId")
        self.test_jar_dependencies = test_jar_dependencies(project)
        self.test_jar_phases = test_jar_execution_phases(project)
        self.test_types = java_types(self.directory, "test")
        self.main_types = java_types(self.directory, "main")


def referenced_foreign_test_types(module, foreign_test_types):
    """Test types from other modules that this module's test sources use.

    Imports catch most of it. The rest is same-package usage, which needs no
    import at all and is easy to introduce without noticing: several
    schema-util and operator modules put test classes in packages that another
    module also contributes to.
    """
    references = {}
    # A same-named type declared by this module wins over the foreign one, so
    # seeing the simple name in the source proves nothing about the dependency.
    own_simple_names = {
        simple_name_of(name)
        for name in list(module.test_types) + list(module.main_types)
    }
    by_package = {}
    for qualified_name in foreign_test_types:
        by_package.setdefault(package_of(qualified_name), []).append(qualified_name)

    for qualified_name, path in module.test_types.items():
        with open(path, encoding="utf-8", errors="replace") as source_file:
            source = source_file.read()
        imports = set(IMPORT_PATTERN.findall(source))
        # A static import names a member, so its declaring type is one segment up.
        imports.update(package_of(entry) for entry in list(imports))
        visible_packages = {package_of(qualified_name)}
        visible_packages.update(
            package_of(entry) for entry in imports if entry.endswith(".*")
        )

        candidates = set(imports)
        for package in visible_packages:
            candidates.update(by_package.get(package, ()))

        for candidate in candidates:
            if candidate not in foreign_test_types:
                continue
            simple_name = simple_name_of(candidate)
            if simple_name in own_simple_names:
                continue
            if re.search(r"\b%s\b" % re.escape(simple_name), source):
                references.setdefault(foreign_test_types[candidate], set()).add(candidate)
    return references


def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    modules = []
    for pom_path in find_poms(root):
        try:
            project = ElementTree.parse(pom_path).getroot()
        except ElementTree.ParseError as error:
            print("Could not parse %s: %s" % (os.path.relpath(pom_path, root), error))
            return 1
        modules.append(Module(pom_path, project))

    by_artifact_id = {module.artifact_id: module for module in modules}

    # Owner of every test type in the reactor. Types declared by more than one
    # module cannot be attributed, so they are left out rather than guessed.
    owners = {}
    ambiguous = set()
    for module in modules:
        for qualified_name in module.test_types:
            if qualified_name in owners:
                ambiguous.add(qualified_name)
            owners[qualified_name] = module.artifact_id
    for qualified_name in ambiguous:
        del owners[qualified_name]

    violations = []

    for module in sorted(modules, key=lambda m: m.artifact_id or ""):
        if not module.test_types:
            continue
        foreign = {
            qualified_name: owner
            for qualified_name, owner in owners.items()
            if owner != module.artifact_id
        }
        for producer, used in sorted(referenced_foreign_test_types(module, foreign).items()):
            if producer in module.test_jar_dependencies:
                continue
            violations.append(
                "%s uses test classes from %s but declares no test-jar dependency on it:\n"
                "    %s\n"
                "  Add to %s:\n"
                "    <dependency>\n"
                "      <groupId>io.apicurio</groupId>\n"
                "      <artifactId>%s</artifactId>\n"
                "      <version>${project.version}</version>\n"
                "      <type>test-jar</type>\n"
                "      <scope>test</scope>\n"
                "    </dependency>"
                % (
                    module.artifact_id,
                    producer,
                    "\n    ".join(sorted(used)),
                    os.path.relpath(module.pom_path, root),
                    producer,
                )
            )

    consumers_by_producer = {}
    for module in modules:
        for producer_id in module.test_jar_dependencies:
            # A producer outside the reactor comes from a repository, so there
            # is no build ordering to get wrong.
            if producer_id in by_artifact_id:
                consumers_by_producer.setdefault(producer_id, set()).add(module.artifact_id)

    for producer_id, consumer_ids in sorted(consumers_by_producer.items()):
        producer = by_artifact_id[producer_id]
        consumed_by = "consumed by " + ", ".join(sorted(consumer_ids))
        phases = producer.test_jar_phases
        if not phases:
            violations.append(
                "%s never runs the maven-jar-plugin test-jar goal, so it produces no\n"
                "  test-jar (%s)." % (os.path.relpath(producer.pom_path, root), consumed_by)
            )
            continue
        off_phase = [phase for phase in phases if phase not in ("", "package")]
        if off_phase:
            violations.append(
                "%s binds the maven-jar-plugin test-jar goal to '%s' instead of package,\n"
                "  so the test-jar is not part of the module's normal output (%s)."
                % (os.path.relpath(producer.pom_path, root), off_phase[0], consumed_by)
            )

    if violations:
        print("Inter-module test dependency problems found:")
        for violation in violations:
            print("\n  " + violation)
        return 1

    consumers = sum(1 for module in modules if module.test_jar_dependencies)
    print("Inter-module test dependencies ok: %d test-jar consumers checked" % consumers)
    return 0


if __name__ == "__main__":
    sys.exit(main())
