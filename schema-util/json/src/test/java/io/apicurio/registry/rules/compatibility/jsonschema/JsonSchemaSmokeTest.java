package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

/**
 * Runs the shared catalogue against both checkers. Where they disagree, the case records which one
 * is wrong and why: {@code skipLegacy} where the legacy checker gives a wrong answer that the
 * Apitomy checker gets right, {@code skipApitomy} where the Apitomy checker is deliberately more
 * conservative than the legacy one. The expectation is always the correct verdict.
 */
class JsonSchemaSmokeTest {

    @Test
    void testCompatibilityLegacy() throws Exception {
        var executor = new CompatibilityTestExecutor(new JsonSchemaCompatibilityChecker(), "skipLegacy");
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }

    @Test
    void testCompatibilityApitomy() throws Exception {
        var executor = new CompatibilityTestExecutor(new ApitomyJsonSchemaCompatibilityChecker(), "skipApitomy");
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
