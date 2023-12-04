package io.apicurio.registry.rules.compatibility.avro;

import io.apicurio.registry.rules.compatibility.AvroCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;


class AvroSchemaSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new AvroCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
