package io.apicurio.registry.rules.compatibility;

import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class ModelSchemaCompatibilityCheckerSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new ModelSchemaCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "model-schema-compatibility-test-data.json")));
    }
}
