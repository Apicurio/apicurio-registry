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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.datamodel.TenantResource;
import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;
import io.apicurio.registry.mt.MultitenancyConfig;
import io.apicurio.registry.mt.TenantContext;
import io.quarkus.runtime.StartupEvent;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantLimitsConfigurationService {

    @Inject
    Logger logger;

    @Inject
    RegistryConfigService configService;

    @Inject
    TenantContext tenantContext;

    @Inject
    MultitenancyConfig mtProperties;

    Long limitsCheckPeriod;
    Long defaultMaxTotalSchemas;
    Long defaultMaxArtifacts;
    Long defaultMaxVersionsPerArtifact;
    Long defaultMaxArtifactProperties;
    Long defaultMaxPropertyKeyBytesSize;
    Long defaultMaxPropertyValueBytesSize;
    Long defaultMaxArtifactLabels;
    Long defaultMaxLabelBytesSize;
    Long defaultMaxNameLength;
    Long defaultMaxDescriptionLength;

    private boolean isConfigured = true;
    private TenantLimitsConfiguration defaultLimitsConfiguration;

    @PostConstruct
    void init() {
        limitsCheckPeriod = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_CONFIG_CACHE_CHECK_PERIOD, Long.class);
        defaultMaxTotalSchemas = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_TOTAL_SCHEMAS, Long.class);
        defaultMaxArtifacts = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_ARTIFACTS, Long.class);
        defaultMaxVersionsPerArtifact = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_VERSIONS_PER_ARTIFACT, Long.class);
        defaultMaxArtifactProperties = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_ARTIFACT_PROPS, Long.class);
        defaultMaxPropertyKeyBytesSize = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_PROPERTY_KEY_BYTES, Long.class);
        defaultMaxPropertyValueBytesSize = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_PROPERTY_VALUE_BYTES, Long.class);
        defaultMaxArtifactLabels = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_ARTIFACT_LABELS, Long.class);
        defaultMaxLabelBytesSize = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_LABEL_SIZE, Long.class);
        defaultMaxNameLength = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_NAME_LENGTH, Long.class);
        defaultMaxDescriptionLength = configService.get(RegistryConfigProperty.REGISTRY_LIMITS_DEFAULT_MAX_DESCRIPTION_LENGTH, Long.class);
    }

    public void onStart(@Observes StartupEvent ev) {

        //when multitenancy is enabled the limits service is
        //always enabled/configured because configuration is loaded dynamically at tenant's first request
        if (!mtProperties.isMultitenancyEnabled()) {

            if (defaultMaxTotalSchemas < 0 &&
                    defaultMaxArtifacts < 0 &&
                    defaultMaxVersionsPerArtifact < 0 &&

                    defaultMaxArtifactProperties < 0 &&
                    defaultMaxPropertyKeyBytesSize < 0 &&
                    defaultMaxPropertyValueBytesSize < 0 &&

                    defaultMaxArtifactLabels < 0 &&
                    defaultMaxLabelBytesSize < 0 &&

                    defaultMaxNameLength < 0 &&
                    defaultMaxDescriptionLength < 0) {

                //all limits are disabled and multitenancy is not enabled
                //limits will not be applied
                isConfigured = false;

            }

        }

        TenantLimitsConfiguration c = new TenantLimitsConfiguration();

        c.setMaxTotalSchemas(defaultMaxTotalSchemas);
        c.setMaxArtifacts(defaultMaxArtifacts);
        c.setMaxVersionsPerArtifact(defaultMaxVersionsPerArtifact);

        c.setMaxArtifactProperties(defaultMaxArtifactProperties);
        c.setMaxPropertyKeyBytesSize(defaultMaxPropertyKeyBytesSize);
        c.setMaxPropertyValueBytesSize(defaultMaxPropertyValueBytesSize);

        c.setMaxArtifactLabels(defaultMaxArtifactLabels);
        c.setMaxLabelBytesSize(defaultMaxLabelBytesSize);

        c.setMaxNameLength(defaultMaxNameLength);
        c.setMaxDescriptionLength(defaultMaxDescriptionLength);

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
    public TenantLimitsConfiguration fromTenantMetadata(RegistryTenant tenantMetadata) {
        TenantLimitsConfiguration c = new TenantLimitsConfiguration();

        if (tenantMetadata.getResources() == null || tenantMetadata.getResources().isEmpty()) {
            logger.debug("Tenant has no resources config, using default tenant limits config");
            return defaultLimitsConfiguration;
        }

        Map<ResourceType, TenantResource> config = tenantMetadata.getResources()
            .stream()
            .collect(Collectors.toMap(tr -> tr.getType(), tr -> tr));

        for (ResourceType type : ResourceType.values()) {

            Long limit = Optional.ofNullable(config.get(type))
                    .map(TenantResource::getLimit)
                    .orElse(null);

            switch (type) {
                case MAX_TOTAL_SCHEMAS_COUNT:
                    c.setMaxTotalSchemas(limit == null ? defaultMaxTotalSchemas : limit);
                    break;
                case MAX_ARTIFACTS_COUNT:
                    c.setMaxArtifacts(limit == null ? defaultMaxArtifacts : limit);
                    break;
                case MAX_VERSIONS_PER_ARTIFACT_COUNT:
                    c.setMaxVersionsPerArtifact(limit == null ? defaultMaxVersionsPerArtifact : limit);
                    break;

                case MAX_ARTIFACT_PROPERTIES_COUNT:
                    c.setMaxArtifactProperties(limit == null ? defaultMaxArtifactProperties : limit);
                    break;
                case MAX_PROPERTY_KEY_SIZE_BYTES:
                    c.setMaxPropertyKeyBytesSize(limit == null ? defaultMaxPropertyKeyBytesSize : limit);
                    break;
                case MAX_PROPERTY_VALUE_SIZE_BYTES:
                    c.setMaxPropertyValueBytesSize(limit == null ? defaultMaxPropertyValueBytesSize : limit);
                    break;

                case MAX_ARTIFACT_LABELS_COUNT:
                    c.setMaxArtifactLabels(limit == null ? defaultMaxArtifactLabels : limit);
                    break;
                case MAX_LABEL_SIZE_BYTES:
                    c.setMaxLabelBytesSize(limit == null ? defaultMaxLabelBytesSize : limit);
                    break;

                case MAX_ARTIFACT_NAME_LENGTH_CHARS:
                    c.setMaxNameLength(limit == null ? defaultMaxNameLength : limit);
                    break;
                case MAX_ARTIFACT_DESCRIPTION_LENGTH_CHARS:
                    c.setMaxDescriptionLength(limit == null ? defaultMaxDescriptionLength : limit);
                    break;

                default:
                    logger.error("Resource Type unhandled " + type.name());
                    break;
            }
        }

        return c;
    }

}
