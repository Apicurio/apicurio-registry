package io.apicurio.registry.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
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

    private ConfigValue raw(String rawValue) {
        ConfigValue value = mock(ConfigValue.class);
        when(value.getRawValue()).thenReturn(rawValue);
        return value;
    }

    @Test
    void agentsUmbrellaWithoutGateFailsStartupNamingOnlyWhatWasConfigured() {
        config.experimentalFeaturesEnabled = false;
        ExperimentalConfigPropertyDef umbrella = new ExperimentalConfigPropertyDef("apicurio.agents.enabled", "Agents");
        ExperimentalConfigPropertyDef a2a = new ExperimentalConfigPropertyDef("apicurio.a2a.enabled", "A2A");
        when(config.experimentalProperties.getExperimentalConfigProperties()).thenReturn(List.of(umbrella, a2a));
        when(mpConfig.getOptionalValue(umbrella.getName(), Boolean.class)).thenReturn(Optional.of(true));
        ConfigValue umbrellaValue = raw("true");
        when(mpConfig.getConfigValue(umbrella.getName())).thenReturn(umbrellaValue);
        // Follows the umbrella through application.properties, so it is true too
        when(mpConfig.getOptionalValue(a2a.getName(), Boolean.class)).thenReturn(Optional.of(true));
        ConfigValue derived = raw("${apicurio.agents.enabled:false}");
        when(mpConfig.getConfigValue(a2a.getName())).thenReturn(derived);
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("sql"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains("apicurio.agents.enabled"),
                "message should name the umbrella, was: " + thrown.getMessage());
        assertFalse(thrown.getMessage().contains("apicurio.a2a.enabled"),
                "a flag that only follows the umbrella was not configured, was: " + thrown.getMessage());
    }

    @Test
    void featureFlagSetDirectlyIsStillReported() {
        config.experimentalFeaturesEnabled = false;
        ExperimentalConfigPropertyDef a2a = new ExperimentalConfigPropertyDef("apicurio.a2a.enabled", "A2A");
        when(config.experimentalProperties.getExperimentalConfigProperties()).thenReturn(List.of(a2a));
        when(mpConfig.getOptionalValue(a2a.getName(), Boolean.class)).thenReturn(Optional.of(true));
        // For example APICURIO_A2A_ENABLED=true, which overrides the expression
        ConfigValue direct = raw("true");
        when(mpConfig.getConfigValue(a2a.getName())).thenReturn(direct);
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("sql"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains("apicurio.a2a.enabled"), thrown.getMessage());
    }

    @Test
    void umbrellaReadFromAnEnvironmentExpressionStillFailsStartup() {
        config.experimentalFeaturesEnabled = false;
        ExperimentalConfigPropertyDef umbrella = new ExperimentalConfigPropertyDef("apicurio.agents.enabled", "Agents");
        ExperimentalConfigPropertyDef a2a = new ExperimentalConfigPropertyDef("apicurio.a2a.enabled", "A2A");
        when(config.experimentalProperties.getExperimentalConfigProperties()).thenReturn(List.of(umbrella, a2a));
        when(mpConfig.getOptionalValue(umbrella.getName(), Boolean.class)).thenReturn(Optional.of(true));
        ConfigValue fromEnvironment = raw("${AGENTS_ENABLED:true}");
        when(mpConfig.getConfigValue(umbrella.getName())).thenReturn(fromEnvironment);
        when(mpConfig.getOptionalValue(a2a.getName(), Boolean.class)).thenReturn(Optional.of(true));
        ConfigValue derived = raw("${apicurio.agents.enabled:false}");
        when(mpConfig.getConfigValue(a2a.getName())).thenReturn(derived);
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("sql"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains("apicurio.agents.enabled"), thrown.getMessage());
    }

    @Test
    void flagReadFromAnyOtherExpressionStillFailsStartup() {
        config.experimentalFeaturesEnabled = false;
        ExperimentalConfigPropertyDef mcpImport = new ExperimentalConfigPropertyDef("apicurio.mcp-import.enabled", "Import");
        when(config.experimentalProperties.getExperimentalConfigProperties()).thenReturn(List.of(mcpImport));
        when(mpConfig.getOptionalValue(mcpImport.getName(), Boolean.class)).thenReturn(Optional.of(true));
        ConfigValue fromEnvironment = raw("${IMPORT_ENABLED:true}");
        when(mpConfig.getConfigValue(mcpImport.getName())).thenReturn(fromEnvironment);
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("sql"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains("apicurio.mcp-import.enabled"), thrown.getMessage());
    }

    @Test
    void flagReferencingAPropertyThatIsNotAToggleStillFailsStartup() {
        config.experimentalFeaturesEnabled = false;
        ExperimentalConfigPropertyDef a2a = new ExperimentalConfigPropertyDef("apicurio.a2a.enabled", "A2A");
        when(config.experimentalProperties.getExperimentalConfigProperties()).thenReturn(List.of(a2a));
        when(mpConfig.getOptionalValue(a2a.getName(), Boolean.class)).thenReturn(Optional.of(true));
        ConfigValue derived = raw("${some.other.property:true}");
        when(mpConfig.getConfigValue(a2a.getName())).thenReturn(derived);
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.of("sql"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> config.validate());
        assertTrue(thrown.getMessage().contains("apicurio.a2a.enabled"), thrown.getMessage());
    }

    @Test
    void unsetStorageKindDefaultsToSqlAndPasses() {
        // When apicurio.storage.kind is absent the code defaults to "sql", which is not gated.
        config.experimentalFeaturesEnabled = false;
        when(mpConfig.getOptionalValue(STORAGE_KIND, String.class)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> config.validate());
    }
}
