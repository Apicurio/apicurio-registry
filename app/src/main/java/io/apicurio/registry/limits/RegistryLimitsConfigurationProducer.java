package io.apicurio.registry.limits;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

public class RegistryLimitsConfigurationProducer {

    @Inject
    Logger logger;

    // All limits to -1 , which means by default all limits are disabled

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-total-schemas")
    @Info(category = "limits", description = "Max total schemas", availableSince = "2.1.0.Final")
    Long defaultMaxTotalSchemas;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-schema-size.bytes")
    @Info(category = "limits", description = "Max schema size (bytes)", availableSince = "2.2.3.Final")
    Long defaultMaxSchemaSizeBytes;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-artifacts")
    @Info(category = "limits", description = "Max artifacts", availableSince = "2.1.0.Final")
    Long defaultMaxArtifacts;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-versions-per-artifact")
    @Info(category = "limits", description = "Max versions per artifacts", availableSince = "2.1.0.Final")
    Long defaultMaxVersionsPerArtifact;

    // TODO content size
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-artifact-properties")
    @Info(category = "limits", description = "Max artifact properties", availableSince = "2.1.0.Final")
    Long defaultMaxArtifactProperties;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-property-key-size.bytes")
    @Info(category = "limits", description = "Max artifact property key size", availableSince = "2.1.0.Final")
    Long defaultMaxPropertyKeyBytesSize;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-property-value-size.bytes")
    @Info(category = "limits", description = "Max artifact property value size", availableSince = "2.1.0.Final")
    Long defaultMaxPropertyValueBytesSize;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-artifact-labels")
    @Info(category = "limits", description = "Max artifact labels", availableSince = "2.2.3.Final")
    Long defaultMaxArtifactLabels;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-label-size.bytes")
    @Info(category = "limits", description = "Max artifact label size", availableSince = "2.1.0.Final")
    Long defaultMaxLabelBytesSize;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-name-length")
    @Info(category = "limits", description = "Max artifact name length", availableSince = "2.1.0.Final")
    Long defaultMaxNameLength;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-description-length")
    @Info(category = "limits", description = "Max artifact description length", availableSince = "2.1.0.Final")
    Long defaultMaxDescriptionLength;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "apicurio.limits.config.max-requests-per-second")
    @Info(category = "limits", description = "Max artifact requests per second", availableSince = "2.2.3.Final")
    Long defaultMaxRequestsPerSecond;

    private boolean isConfigured = true;
    private RegistryLimitsConfiguration defaultLimitsConfiguration;

    @Produces
    @ApplicationScoped
    public RegistryLimitsConfiguration postConstruct() {

        RegistryLimitsConfiguration c = new RegistryLimitsConfiguration();

        c.setMaxTotalSchemasCount(defaultMaxTotalSchemas);
        c.setMaxSchemaSizeBytes(defaultMaxSchemaSizeBytes);
        c.setMaxArtifactsCount(defaultMaxArtifacts);
        c.setMaxVersionsPerArtifactCount(defaultMaxVersionsPerArtifact);

        c.setMaxArtifactPropertiesCount(defaultMaxArtifactProperties);
        c.setMaxPropertyKeySizeBytes(defaultMaxPropertyKeyBytesSize);
        c.setMaxPropertyValueSizeBytes(defaultMaxPropertyValueBytesSize);

        c.setMaxArtifactLabelsCount(defaultMaxArtifactLabels);
        c.setMaxLabelSizeBytes(defaultMaxLabelBytesSize);

        c.setMaxArtifactNameLengthChars(defaultMaxNameLength);
        c.setMaxArtifactDescriptionLengthChars(defaultMaxDescriptionLength);

        c.setMaxRequestsPerSecondCount(defaultMaxRequestsPerSecond);

        defaultLimitsConfiguration = c;

        return defaultLimitsConfiguration;
    }

    public boolean isConfigured() {
        return this.isConfigured;
    }
}
