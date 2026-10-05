package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

/**
 * Runs the catalogue against the checker. The expectation is always the correct verdict; a case the
 * checker gets wrong records why in {@code skipApitomy}, such as being deliberately more
 * conservative than necessary.
 */
class JsonSchemaSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new ApitomyJsonSchemaCompatibilityChecker(), "skipApitomy");
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
