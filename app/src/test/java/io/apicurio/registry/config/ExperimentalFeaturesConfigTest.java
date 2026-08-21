package io.apicurio.registry.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.ExperimentalConfigPropertyDef;
import io.apicurio.common.apps.config.ExperimentalConfigPropertyList;

/**
 * Unit tests for {@link ExperimentalFeaturesConfig#validate()}. These exercise the gating logic
 * directly. The bean's package-private fields and {@code validate()} method are accessible because
 * this test lives in the same package.
 */
class ExperimentalFeaturesConfigTest {

    private static final String STORAGE_KIND = "apicurio.storage.kind";

    private ExperimentalFeaturesConfig config;
    private Config mpConfig;

    @BeforeEach
    void setup() {
        config = new ExperimentalFeaturesConfig();
        config.log = mock(Logger.class);
        config.experimentalProperties = mock(ExperimentalConfigPropertyList.class);
        when(config.experimentalProperties.getExperimentalConfigProperties()).thenReturn(List.of());
        mpConfig = mock(Config.class);
        config.config = mpConfig;
    }

    @Test
    void gateEnabledSkipsAllValidation() {
        // Gate open: even an otherwise-gated storage variant must not trip validation. The method
        // returns before touching config, so no storage-kind stubbing is required.
        config.experimentalFeaturesEnabled = true;

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    void defaultSqlStorageWithoutExperimentalFeaturesPasses() {
        config.experimentalFeaturesEnabled = false;
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("sql"));

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    void gitopsStorageWithoutGateFailsStartup() {
        config.experimentalFeaturesEnabled = false;
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("gitops"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains("apicurio.storage.kind=gitops"),
                "message should name the gitops violation, was: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("apicurio.features.experimental.enabled"),
                "message should point at the gate property, was: " + thrown.getMessage());
    }

    @Test
    void kubernetesopsStorageWithoutGateFailsStartup() {
        config.experimentalFeaturesEnabled = false;
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class))
                .thenReturn(Optional.of("kubernetesops"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains("apicurio.storage.kind=kubernetesops"),
                "message should name the kubernetesops violation, was: " + thrown.getMessage());
    }

    @Test
    void enabledExperimentalPropertyWithoutGateFailsStartup() {
        config.experimentalFeaturesEnabled = false;
        ExperimentalConfigPropertyDef property = new ExperimentalConfigPropertyDef(
                "apicurio.registry.some-feature.enabled", "Some experimental feature");
        when(config.experimentalProperties.getExperimentalConfigProperties()).thenReturn(List.of(property));
        when(mpConfig.getOptionalValue(property.getName(), Boolean.class)).thenReturn(Optional.of(true));
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("sql"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains(property.getName()),
                "message should name the experimental property, was: " + thrown.getMessage());
    }

    @Test
    void unsetStorageKindDefaultsToSqlAndPasses() {
        // When apicurio.storage.kind is absent the code defaults to "sql", which is not gated.
        config.experimentalFeaturesEnabled = false;
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> config.validate());
    }
}
