package io.apicurio.registry.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the agent registry feature (A2A, MCP tools and MCP Registry, AI Catalog, ARD, prompt rendering and
 * its artifact types) isolated from the rest of the registry. Agent code lives in
 * {@code io.apicurio.registry.agents..} and may use core freely; core may reach it only through the
 * extension points in {@code io.apicurio.registry.extensions} and the ServiceLoader-based artifact type
 * registry, never by referencing an agent class.
 */
class AgentsBoundaryTest {

    private static final String AGENTS = "io.apicurio.registry.agents..";
    private static final String AGENTS_PACKAGE_PREFIX = "io.apicurio.registry.agents.";

    private static JavaClasses registryClasses;

    @BeforeAll
    static void importClasses() {
        registryClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.apicurio.registry");
    }

    @Test
    void coreDoesNotDependOnAgents() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("io.apicurio.registry..")
                .and().resideOutsideOfPackage(AGENTS)
                .should().dependOnClassesThat().resideInAPackage(AGENTS)
                .because("agent registry code must stay pluggable; use io.apicurio.registry.extensions");
        rule.check(registryClasses);
    }

    /**
     * Source-level complement to {@link #coreDoesNotDependOnAgents()}. javac inlines compile-time constants
     * (e.g. a {@code static final String} from an agent class) into the caller, leaving no dependency in the
     * bytecode for ArchUnit to see. Any reference to the agents package in core source is caught here.
     */
    @Test
    void coreSourceDoesNotReferenceAgents() throws IOException {
        Path sources = Path.of("src", "main", "java");
        Path agents = sources.resolve(Path.of("io", "apicurio", "registry", "agents"));
        assertTrue(Files.isDirectory(sources), "run from the app module: " + sources.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.startsWith(agents)).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains(AGENTS_PACKAGE_PREFIX)) {
                        violations.add(sources.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
                "core source must not reference agent code; use io.apicurio.registry.extensions");
    }
}
