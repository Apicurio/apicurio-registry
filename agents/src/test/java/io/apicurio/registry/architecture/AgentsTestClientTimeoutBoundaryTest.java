package io.apicurio.registry.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.auth.grants.GrantsAgentsAuthorizationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies {@link TestClientTimeoutRule} to the agents module's test classes, which the app module's
 * {@link TestClientTimeoutBoundaryTest} cannot see. The app test classes arrive here in the app test-jar, which
 * the test-only import option leaves out, so they stay checked by the app module alone. No class is allowlisted.
 */
class AgentsTestClientTimeoutBoundaryTest {

    @Test
    void agentsTestClientsStartFromTheBoundedOptions() {
        JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("io.apicurio.registry");
        // Guards against a vacuous pass: the import must see this module's tests, one of which builds a client.
        assertTrue(testClasses.contain(GrantsAgentsAuthorizationTest.class),
                "the ArchUnit import found no agents test classes");
        assertFalse(testClasses.contain(AbstractResourceTestBase.class),
                "the ArchUnit import picked up the app test-jar, whose classes the app module checks");

        DescribedPredicate<JavaClass> agentsTests = DescribedPredicate.describe("are agents test classes", c -> true);
        TestClientTimeoutRule.rule(agentsTests, TestClientTimeoutRule.helper()).check(testClasses);
    }
}
