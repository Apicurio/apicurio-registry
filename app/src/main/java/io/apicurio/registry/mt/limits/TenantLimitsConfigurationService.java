/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.mt.limits;

import io.apicurio.common.apps.config.Info;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenant;
import io.apicurio.tenantmanager.api.datamodel.ResourceType;
import io.apicurio.tenantmanager.api.datamodel.TenantResource;
import io.apicurio.registry.mt.MultitenancyProperties;
import io.apicurio.registry.mt.TenantContext;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import static io.apicurio.tenantmanager.api.datamodel.ResourceType.*;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantLimitsConfigurationService {

    @Inject
    Logger logger;

    @Inject
    @ConfigProperty(defaultValue = "30000", name = "registry.limits.config.cache.check-period")
    @Info(category = "limits", description = "Cache check period limit", availableSince = "2.1.0.Final")
    Long limitsCheckPeriod;

    //All limits to -1 , which means by default all limits are disabled

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-total-schemas")
    @Info(category = "limits", description = "Max total schemas", availableSince = "2.1.0.Final")
    Long defaultMaxTotalSchemas;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-schema-size-bytes")
    @Info(category = "limits", description = "Max schema size (bytes)", availableSince = "2.2.3.Final")
    Long defaultMaxSchemaSizeBytes;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-artifacts")
    @Info(category = "limits", description = "Max artifacts", availableSince = "2.1.0.Final")
    Long defaultMaxArtifacts;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-versions-per-artifact")
    @Info(category = "limits", description = "Max versions per artifacts", availableSince = "2.1.0.Final")
    Long defaultMaxVersionsPerArtifact;

    //TODO content size
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-artifact-properties")
    @Info(category = "limits", description = "Max artifact properties", availableSince = "2.1.0.Final")
    Long defaultMaxArtifactProperties;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-property-key-size")
    @Info(category = "limits", description = "Max artifact property key size", availableSince = "2.1.0.Final")
    Long defaultMaxPropertyKeyBytesSize;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-property-value-size")
    @Info(category = "limits", description = "Max artifact property value size", availableSince = "2.1.0.Final")
    Long defaultMaxPropertyValueBytesSize;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-artifact-labels")
    @Info(category = "limits", description = "Max artifact labels", availableSince = "2.2.3.Final")
    Long defaultMaxArtifactLabels;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-label-size")
    @Info(category = "limits", description = "Max artifact label size", availableSince = "2.1.0.Final")
    Long defaultMaxLabelBytesSize;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-name-length")
    @Info(category = "limits", description = "Max artifact name length", availableSince = "2.1.0.Final")
    Long defaultMaxNameLength;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-description-length")
    @Info(category = "limits", description = "Max artifact description length", availableSince = "2.1.0.Final")
    Long defaultMaxDescriptionLength;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-requests-per-second")
    @Info(category = "limits", description = "Max artifact requests per second", availableSince = "2.2.3.Final")
    Long defaultMaxRequestsPerSecond;

    @Inject
    TenantContext tenantContext;

    @Inject
    MultitenancyProperties mtProperties;

    private boolean isConfigured = true;
    private TenantLimitsConfiguration defaultLimitsConfiguration;

    public void onStart(@Observes StartupEvent ev) {

        //when multi-tenancy is enabled the limits service is
        //always enabled/configured because configuration is loaded dynamically at tenant's first request
        if (!mtProperties.isMultitenancyEnabled()) {

            if (defaultMaxTotalSchemas < 0 &&
                    defaultMaxSchemaSizeBytes < 0 &&
                    defaultMaxArtifacts < 0 &&
                    defaultMaxVersionsPerArtifact < 0 &&

                    defaultMaxArtifactProperties < 0 &&
                    defaultMaxPropertyKeyBytesSize < 0 &&
                    defaultMaxPropertyValueBytesSize < 0 &&

                    defaultMaxArtifactLabels < 0 &&
                    defaultMaxLabelBytesSize < 0 &&

                    defaultMaxNameLength < 0 &&
                    defaultMaxDescriptionLength < 0 &&

                    defaultMaxRequestsPerSecond < 0
                ) {

                //all limits are disabled and multi-tenancy is not enabled
                //limits will not be applied
                isConfigured = false;

            }

        }

        TenantLimitsConfiguration c = new TenantLimitsConfiguration();

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
    }

    public boolean isConfigured() {
        return this.isConfigured;
    }

    public TenantLimitsConfiguration defaultConfigurationTenant() {
        return defaultLimitsConfiguration;
    }

    /**
     * @param tenantMetadata
     */
    public TenantLimitsConfiguration fromTenantMetadata(ApicurioTenant tenantMetadata) {
        TenantLimitsConfiguration c = new TenantLimitsConfiguration();

        if (tenantMetadata.getResources() == null || tenantMetadata.getResources().isEmpty()) {
            logger.debug("Tenant has no resources config, using default tenant limits config");
            return defaultLimitsConfiguration;
        }

        // TODO: refactor this code
        Map<String, TenantResource> config = tenantMetadata.getResources()
            .stream()
            .collect(Collectors.toMap(tr -> tr.getType(), tr -> tr));

        for (String type : ResourceType.values()) {

            Long limit = Optional.ofNullable(config.get(type))
                    .map(TenantResource::getLimit)
                    .orElse(null);

            switch (type) {
                case MAX_TOTAL_SCHEMAS_COUNT:
                    c.setMaxTotalSchemasCount(limit == null ? defaultMaxTotalSchemas : limit);
                    break;
                case MAX_SCHEMA_SIZE_BYTES:
                    c.setMaxSchemaSizeBytes(limit == null ? defaultMaxSchemaSizeBytes : limit);
                    break;
                case MAX_ARTIFACTS_COUNT:
                    c.setMaxArtifactsCount(limit == null ? defaultMaxArtifacts : limit);
                    break;
                case MAX_VERSIONS_PER_ARTIFACT_COUNT:
                    c.setMaxVersionsPerArtifactCount(limit == null ? defaultMaxVersionsPerArtifact : limit);
                    break;

                case MAX_ARTIFACT_PROPERTIES_COUNT:
                    c.setMaxArtifactPropertiesCount(limit == null ? defaultMaxArtifactProperties : limit);
                    break;
                case MAX_PROPERTY_KEY_SIZE_BYTES:
                    c.setMaxPropertyKeySizeBytes(limit == null ? defaultMaxPropertyKeyBytesSize : limit);
                    break;
                case MAX_PROPERTY_VALUE_SIZE_BYTES:
                    c.setMaxPropertyValueSizeBytes(limit == null ? defaultMaxPropertyValueBytesSize : limit);
                    break;

                case MAX_ARTIFACT_LABELS_COUNT:
                    c.setMaxArtifactLabelsCount(limit == null ? defaultMaxArtifactLabels : limit);
                    break;
                case MAX_LABEL_SIZE_BYTES:
                    c.setMaxLabelSizeBytes(limit == null ? defaultMaxLabelBytesSize : limit);
                    break;

                case MAX_ARTIFACT_NAME_LENGTH_CHARS:
                    c.setMaxArtifactNameLengthChars(limit == null ? defaultMaxNameLength : limit);
                    break;
                case MAX_ARTIFACT_DESCRIPTION_LENGTH_CHARS:
                    c.setMaxArtifactDescriptionLengthChars(limit == null ? defaultMaxDescriptionLength : limit);
                    break;
                case MAX_REQUESTS_PER_SECOND_COUNT:
                    c.setMaxRequestsPerSecondCount(limit == null ? defaultMaxRequestsPerSecond : limit);
                    break;

                default:
                    logger.error("Resource Type unhandled " + type);
                    break;
            }
        }

        return c;
    }

}
