package io.apicurio.registry.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Singleton;

@QuarkusTest
class ExperimentalFeaturesConfigStartupTest {

    @Test
    void startupCreatesExperimentalFeaturesConfig() {
        final InjectableContext context = Arc.container().getActiveContext(Singleton.class);
        final boolean created = context.getState().getContextualInstances().keySet().stream()
                .anyMatch(bean -> ExperimentalFeaturesConfig.class.equals(bean.getBeanClass()));

        assertTrue(created, "@Startup should instantiate the experimental features gate before tests run");
    }
}
