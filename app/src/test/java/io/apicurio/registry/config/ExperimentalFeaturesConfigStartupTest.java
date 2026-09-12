package io.apicurio.registry.config;

import static org.junit.jupiter.api.Assertions.fail;

import io.apicurio.registry.logging.LoggerProducer;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExperimentalFeaturesConfigStartupTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(ExperimentalFeaturesConfig.class, LoggerProducer.class))
            .withRuntimeConfiguration("""
                    apicurio.storage.kind=gitops
                    apicurio.features.experimental.enabled=false
                    """)
            .assertException(ExperimentalFeaturesConfigStartupTest::assertGateFailure);

    @Test
    void startupFailsWhenExperimentalStorageIsEnabledWithoutGate() {
        // QuarkusUnitTest performs the assertion while starting the application.
    }

    private static void assertGateFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && current.getMessage().contains("apicurio.storage.kind=gitops")
                    && current.getMessage().contains("apicurio.features.experimental.enabled")) {
                return;
            }
            current = current.getCause();
        }
        fail("startup failed for an unexpected reason: " + failure);
    }
}
