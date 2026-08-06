package io.apicurio.registry.rules.compatibility.protobuf;

import io.apicurio.registry.protobuf.rules.compatibility.ProtobufCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor;
import org.junit.jupiter.api.Test;

class ProtobufCompatibilityCheckerSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new ProtobufCompatibilityChecker());
        CompatibilityTestExecutor.throwOnFailure(executor
                .execute(CompatibilityTestExecutor.readResource(this.getClass(), "compatibility-test-data.json")));
    }
}
