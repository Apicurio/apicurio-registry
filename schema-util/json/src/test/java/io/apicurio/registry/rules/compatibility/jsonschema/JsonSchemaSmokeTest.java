package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor;
import org.junit.jupiter.api.Test;

class JsonSchemaSmokeTest {

    @Test
    void testCompatibilityLegacy() throws Exception {
        var executor = new CompatibilityTestExecutor(new JsonSchemaCompatibilityChecker());
        CompatibilityTestExecutor.throwOnFailure(executor.execute(CompatibilityTestExecutor.readResource(this.getClass(), "compatibility-test-data.json")));
    }

    @Test
    void testCompatibilityApitomy() throws Exception {
        var executor = new CompatibilityTestExecutor(new ApitomyJsonSchemaCompatibilityChecker(), "skipApitomy");
        CompatibilityTestExecutor.throwOnFailure(executor.execute(CompatibilityTestExecutor.readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
