package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class JsonSchemaSmokeTest {

    @Test
    void testCompatibilityLegacy() throws Exception {
        var executor = new CompatibilityTestExecutor(new JsonSchemaCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }

    @Test
    void testCompatibilityApitomy() throws Exception {
        var executor = new CompatibilityTestExecutor(new ApitomyJsonSchemaCompatibilityChecker(), "skipApitomy");
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
