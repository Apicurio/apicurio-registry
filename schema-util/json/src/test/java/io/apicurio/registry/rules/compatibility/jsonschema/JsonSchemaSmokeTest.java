package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityChecker;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class JsonSchemaSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new JsonSchemaCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
