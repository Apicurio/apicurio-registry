package io.apicurio.registry.config;

import io.apicurio.common.apps.config.ExperimentalConfigPropertyDef;
import io.apicurio.common.apps.config.ExperimentalConfigPropertyList;
import io.quarkus.runtime.Startup;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ExperimentalFeaturesConfig}.
 */
public class ExperimentalFeaturesConfigTest {

    private static ExperimentalFeaturesConfig configWith(boolean gateEnabled,
            List<ExperimentalConfigPropertyDef> experimentalProperties, Map<String, String> properties) {
        ExperimentalFeaturesConfig config = new ExperimentalFeaturesConfig();
        config.log = LoggerFactory.getLogger(ExperimentalFeaturesConfigTest.class);
        config.config = new SmallRyeConfigBuilder().withDefaultValues(properties).build();
        config.experimentalProperties = new ExperimentalConfigPropertyList(experimentalProperties);
        config.experimentalFeaturesEnabled = gateEnabled;
        return config;
    }

    /**
     * The gate is only useful if it is actually evaluated during startup. {@code validate()} is a
     * {@code @PostConstruct} callback, and CDI only runs it once the bean is instantiated. Nothing injects
     * this bean, so without {@code @Startup} it is never created and the whole gate is inert (#9630).
     */
    @Test
    void testValidationIsWiredToStartup() {
        assertTrue(ExperimentalFeaturesConfig.class.isAnnotationPresent(Startup.class),
                "ExperimentalFeaturesConfig must be annotated with @Startup; nothing injects it, so without "
                        + "eager instantiation @PostConstruct validate() never runs and the gate is inert.");
    }

    @Test
    void testGitopsStorageRejectedWhenGateDisabled() {
        ExperimentalFeaturesConfig config = configWith(false, List.of(),
                Map.of("apicurio.storage.kind", "gitops"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(thrown.getMessage().contains("apicurio.storage.kind=gitops"), thrown.getMessage());
    }

    @Test
    void testKubernetesOpsStorageRejectedWhenGateDisabled() {
        ExperimentalFeaturesConfig config = configWith(false, List.of(),
                Map.of("apicurio.storage.kind", "kubernetesops"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(thrown.getMessage().contains("apicurio.storage.kind=kubernetesops"), thrown.getMessage());
    }

    @Test
    void testExperimentalTogglePropertyRejectedWhenGateDisabled() {
        ExperimentalFeaturesConfig config = configWith(false,
                List.of(new ExperimentalConfigPropertyDef("apicurio.iceberg.enabled", "Iceberg support")),
                Map.of("apicurio.iceberg.enabled", "true"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(thrown.getMessage().contains("apicurio.iceberg.enabled"), thrown.getMessage());
    }

    @Test
    void testExperimentalFeaturesAllowedWhenGateEnabled() {
        ExperimentalFeaturesConfig config = configWith(true,
                List.of(new ExperimentalConfigPropertyDef("apicurio.iceberg.enabled", "Iceberg support")),
                Map.of("apicurio.storage.kind", "gitops", "apicurio.iceberg.enabled", "true"));

        assertDoesNotThrow(config::validate);
    }

    @Test
    void testDefaultConfigurationIsAccepted() {
        ExperimentalFeaturesConfig config = configWith(false,
                List.of(new ExperimentalConfigPropertyDef("apicurio.iceberg.enabled", "Iceberg support")),
                Map.of("apicurio.storage.kind", "sql", "apicurio.iceberg.enabled", "false"));

        assertDoesNotThrow(config::validate);
    }
}
