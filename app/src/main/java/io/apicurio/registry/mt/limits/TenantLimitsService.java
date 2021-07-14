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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.metrics.StorageMetricsStore;

/**
 * Component that provides the logic to enforce the limits in the usage of the registry
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantLimitsService {

    //FIXME improve error messages
    private static final String MAX_TOTAL_SCHEMAS_EXCEEDED_MSG = "Maximum number of uploaded schemas exceeded";
    private static final String MAX_ARTIFACTS_EXCEEDED_MSG = "Maximum number of artifacts exceeded";
    private static final String MAX_VERSIONS_PER_ARTIFACT_EXCEEDED_MSG = "Maximum number of versions exceeded for this artifact";
    private static final String MAX_NAME_LENGTH_EXCEEDED_MSG = "Maximum artifact name lenght exceeded";
    private static final String MAX_DESC_LENGTH_EXCEEDED_MSG = "Maximum artifact description lenght exceeded";
    private static final String MAX_LABELS_EXCEEDED_MSG = "Maximum number of labels exceeded for this artifact";
    private static final String MAX_LABEL_SIZE_EXCEEDED_MSG = "Maximum label size exceeded";
    private static final String MAX_PROPERTIES_EXCEEDED_MSG = "Maximum number of properties exceeded for this artifact";
    private static final String MAX_PROP_KEY_SIZE_EXCEEDED_MSG = "Maximum property key size exceeded";
    private static final String MAX_PROP_VALUE_SIZE_EXCEEDED_MSG = "Maximum property value size exceeded";

    @Inject
    Logger log;

    @Inject
    TenantContext tenantContext;

    @Inject
    StorageMetricsStore storageMetricsStore;

    private LimitsCheckResult checkTotalSchemas() {

        if (isLimitDisabled(TenantLimitsConfiguration::getMaxTotalSchemas)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentTotalSchemas = storageMetricsStore.getOrInitializeTotalSchemasCounter();

        if (currentTotalSchemas < tenantContext.limitsConfig().getMaxTotalSchemas()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current total schemas {} , max total schemas {}", currentTotalSchemas, tenantContext.limitsConfig().getMaxTotalSchemas());
            return LimitsCheckResult.disallowed(MAX_TOTAL_SCHEMAS_EXCEEDED_MSG);
        }
    }

    public LimitsCheckResult canCreateArtifact(EditableArtifactMetaDataDto meta) {

        LimitsCheckResult mr = checkMetaData(meta);
        if (!mr.isAllowed()) {
            return mr;
        }

        LimitsCheckResult tsr = checkTotalSchemas();
        if (!tsr.isAllowed()) {
            return tsr;
        }

        if (isLimitDisabled(TenantLimitsConfiguration::getMaxArtifacts)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifacts = storageMetricsStore.getOrInitializeArtifactsCounter();

        if (currentArtifacts < tenantContext.limitsConfig().getMaxArtifacts()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current artifacts {} , max artifacts allowed {}", currentArtifacts, tenantContext.limitsConfig().getMaxArtifacts());
            return LimitsCheckResult.disallowed(MAX_ARTIFACTS_EXCEEDED_MSG);
        }
    }

    public LimitsCheckResult canCreateArtifactVersion(String groupId, String artifactId, EditableArtifactMetaDataDto meta) {

        LimitsCheckResult mr = checkMetaData(meta);
        if (!mr.isAllowed()) {
            return mr;
        }

        LimitsCheckResult tsr = checkTotalSchemas();
        if (!tsr.isAllowed()) {
            return tsr;
        }

        if (isLimitDisabled(TenantLimitsConfiguration::getMaxVersionsPerArtifact)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifactVersions = storageMetricsStore.getOrInitializeArtifactVersionsCounter(groupId, artifactId);

        if (currentArtifactVersions < tenantContext.limitsConfig().getMaxVersionsPerArtifact()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current versions per artifact for artifact {}/{} {} , max versions per artifacts allowed {}", groupId, artifactId, currentArtifactVersions, tenantContext.limitsConfig().getMaxVersionsPerArtifact());
            return LimitsCheckResult.disallowed(MAX_VERSIONS_PER_ARTIFACT_EXCEEDED_MSG);
        }
    }

    public void artifactCreated() {
        storageMetricsStore.incrementTotalSchemasCounter();
        storageMetricsStore.incrementArtifactsCounter();
    }

    public void artifactVersionCreated(String groupId, String artifactId) {
        storageMetricsStore.incrementTotalSchemasCounter();
        storageMetricsStore.incrementArtifactVersionsCounter(groupId, artifactId);
    }

    public void artifactDeleted() {
        storageMetricsStore.resetTotalSchemasCounter();
        storageMetricsStore.resetArtifactsCounter();
    }

    public void artifactVersionDeleted(String groupId, String artifactId) {
        storageMetricsStore.resetTotalSchemasCounter();
        storageMetricsStore.resetArtifactVersionsCounter(groupId, artifactId);
    }

    public LimitsCheckResult checkMetaData(EditableArtifactMetaDataDto meta) {
        if (meta == null || tenantContext.limitsConfig() == null) {
            return LimitsCheckResult.ok();
        }
        List<String> errorMessages = new ArrayList<>();

        //name is limited at db level to 512 chars
        if (meta.getName() != null && isLimitEnabled(TenantLimitsConfiguration::getMaxNameLength)) {
            if (meta.getName().length() > tenantContext.limitsConfig().getMaxNameLength()) {
                errorMessages.add(MAX_NAME_LENGTH_EXCEEDED_MSG);
            }
        }

        //description is limited at db level to 1024 chars
        if (meta.getDescription() != null && isLimitEnabled(TenantLimitsConfiguration::getMaxDescriptionLength)) {

            if (meta.getDescription().length() > tenantContext.limitsConfig().getMaxDescriptionLength()) {
                errorMessages.add(MAX_DESC_LENGTH_EXCEEDED_MSG);
            }
        }

        if (meta.getLabels() != null) {
            if (isLimitEnabled(TenantLimitsConfiguration::getMaxArtifactLabels) &&
                    meta.getLabels().size() > tenantContext.limitsConfig().getMaxArtifactLabels()) {

                errorMessages.add(MAX_LABELS_EXCEEDED_MSG);

            } else if (isLimitEnabled(TenantLimitsConfiguration::getMaxLabelBytesSize)) {

                meta.getLabels().forEach(l -> {

                    if (l.getBytes(StandardCharsets.UTF_8).length >
                            tenantContext.limitsConfig().getMaxLabelBytesSize()) {
                        errorMessages.add(MAX_LABEL_SIZE_EXCEEDED_MSG);
                    }
                });
            }
        }

        if (meta.getProperties() != null) {
            if (isLimitEnabled(TenantLimitsConfiguration::getMaxArtifactProperties) &&
                    meta.getProperties().size() > tenantContext.limitsConfig().getMaxArtifactProperties()) {

                errorMessages.add(MAX_PROPERTIES_EXCEEDED_MSG);

            } else if (isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyKeyBytesSize) ||
                    isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyValueBytesSize)){

                meta.getProperties().entrySet().forEach(e -> {

                    if (isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyKeyBytesSize) &&
                            e.getKey().length() > tenantContext.limitsConfig().getMaxPropertyKeyBytesSize()) {
                        errorMessages.add(MAX_PROP_KEY_SIZE_EXCEEDED_MSG);
                    }

                    if (isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyValueBytesSize) &&
                            e.getValue().length() > tenantContext.limitsConfig().getMaxPropertyValueBytesSize()) {
                        errorMessages.add(MAX_PROP_VALUE_SIZE_EXCEEDED_MSG);
                    }
                });
            }
        }

        if (errorMessages.isEmpty()) {
            return LimitsCheckResult.ok();
        } else {
            return LimitsCheckResult.disallowed(String.join(", ", errorMessages));
        }
    }

    private boolean isLimitEnabled(Function<TenantLimitsConfiguration, Long> limitGetter) {
        if (tenantContext.limitsConfig() != null) {
            Long limit = limitGetter.apply(tenantContext.limitsConfig());
            if (limit != null && limit >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isLimitDisabled(Function<TenantLimitsConfiguration, Long> limitGetter) {
        return !isLimitEnabled(limitGetter);
    }

}
