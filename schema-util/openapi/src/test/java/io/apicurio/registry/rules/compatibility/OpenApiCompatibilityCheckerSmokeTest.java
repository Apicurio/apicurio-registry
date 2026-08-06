package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.openapi.rules.compatibility.OpenApiCompatibilityChecker;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class OpenApiCompatibilityCheckerSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new OpenApiCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
