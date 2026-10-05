package io.apicurio.registry.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps the agent registry feature (A2A, MCP tools and MCP Registry, AI Catalog, ARD, prompt rendering and
 * its artifact types) isolated from the rest of the registry. Agent code lives in
 * {@code io.apicurio.registry.agents..} and may use core freely; core may reach it only through the
 * extension points in {@code io.apicurio.registry.extensions} and the ServiceLoader-based artifact type
 * registry, never by referencing an agent class.
 */
class AgentsBoundaryTest {

    private static final String AGENTS = "io.apicurio.registry.agents..";

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
}
