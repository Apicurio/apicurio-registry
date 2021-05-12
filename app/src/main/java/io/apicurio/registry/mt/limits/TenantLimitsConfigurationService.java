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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.mt.MultitenancyProperties;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.utils.CheckPeriodCache;
import io.quarkus.runtime.StartupEvent;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantLimitsConfigurationService {

    @Inject
    @ConfigProperty(defaultValue = "30000", name = "registry.limits.config.cache.check-period")
    Long limitsCheckPeriod;

    //All limits to -1 , which means by default all limits are disabled

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-total-schemas")
    Long defaultMaxTotalSchemas;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-artifacts")
    Long defaultMaxArtifacts;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-versions-per-artifact")
    Long defaultMaxVersionsPerArtifact;

    //TODO content size
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-artifact-properties")
    Long defaultMaxArtifactProperties;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-property-key-size")
    Long defaultMaxPropertyKeyBytesSize;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-property-value-size")
    Long defaultMaxPropertyValueBytesSize;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-artifact-lables")
    Long defaultMaxArtifactLabels;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-label-size")
    Long defaultMaxLabelBytesSize;

    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-name-length")
    Long defaultMaxNameLength;
    @Inject
    @ConfigProperty(defaultValue = "-1", name = "registry.limits.config.max-description-length")
    Long defaultMaxDescriptionLength;

    @Inject
    TenantContext tenantContext;

    @Inject
    MultitenancyProperties mtProperties;

    private boolean isConfigured = true;
    private TenantLimitsConfigurationImpl defaultLimitsConfiguration;
    private CheckPeriodCache<String, TenantLimitsConfigurationImpl> limitsConfigurationCache;

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

        limitsConfigurationCache = new CheckPeriodCache<>(limitsCheckPeriod);

        TenantLimitsConfigurationImpl c = new TenantLimitsConfigurationImpl();

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

    /**
     * Returns the configuration for the current tenant
     * @return
     */
    public TenantLimitsConfigurationImpl getConfiguration() {

        return limitsConfigurationCache.compute(tenantContext.tenantId(), k -> {
            //TODO get the limits configuration from the tenant manager, it should be loaded into the tenant context
           return defaultLimitsConfiguration;
        });

    }

}
