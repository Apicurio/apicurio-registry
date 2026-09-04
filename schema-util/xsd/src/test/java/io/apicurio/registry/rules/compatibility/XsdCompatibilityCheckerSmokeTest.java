package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.xsd.rules.compatibility.XsdCompatibilityChecker;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class XsdCompatibilityCheckerSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new XsdCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
