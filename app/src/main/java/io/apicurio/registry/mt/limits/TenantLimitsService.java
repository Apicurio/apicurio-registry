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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.metrics.StorageMetricsStore;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Component that provides the logic to enforce the limits in the usage of the registry
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantLimitsService {

    //FIXME improve error messages
    private static final String MAX_TOTAL_SCHEMAS_EXCEEDED_MSG = "Maximum number of artifact versions exceeded";
    private static final String MAX_SCHEMA_SIZE_EXCEEDED_MSG = "Maximum size of artifact version exceeded";
    private static final String MAX_ARTIFACTS_EXCEEDED_MSG = "Maximum number of artifacts exceeded";
    private static final String MAX_VERSIONS_PER_ARTIFACT_EXCEEDED_MSG = "Maximum number of versions exceeded for this artifact";
    private static final String MAX_NAME_LENGTH_EXCEEDED_MSG = "Maximum artifact name length exceeded";
    private static final String MAX_DESC_LENGTH_EXCEEDED_MSG = "Maximum artifact description length exceeded";
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

        if (isLimitDisabled(TenantLimitsConfiguration::getMaxTotalSchemasCount)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentTotalSchemas = storageMetricsStore.getOrInitializeTotalSchemasCounter();

        if (currentTotalSchemas < tenantContext.limitsConfig().getMaxTotalSchemasCount()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current total schemas {} , max total schemas {}", currentTotalSchemas, tenantContext.limitsConfig().getMaxTotalSchemasCount());
            return LimitsCheckResult.disallowed(MAX_TOTAL_SCHEMAS_EXCEEDED_MSG);
        }
    }

    public LimitsCheckResult canCreateArtifact(EditableArtifactMetaDataDto meta, ContentHandle content) {

        LimitsCheckResult mr = checkMetaData(meta);
        if (!mr.isAllowed()) {
            return mr;
        }

        LimitsCheckResult tsr = checkTotalSchemas();
        if (!tsr.isAllowed()) {
            return tsr;
        }

        LimitsCheckResult ssr = checkSchemaSize(content);
        if (!ssr.isAllowed()) {
            return ssr;
        }

        if (isLimitDisabled(TenantLimitsConfiguration::getMaxArtifactsCount)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifacts = storageMetricsStore.getOrInitializeArtifactsCounter();

        if (currentArtifacts < tenantContext.limitsConfig().getMaxArtifactsCount()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current artifacts {} , max artifacts allowed {}", currentArtifacts, tenantContext.limitsConfig().getMaxArtifactsCount());
            return LimitsCheckResult.disallowed(MAX_ARTIFACTS_EXCEEDED_MSG);
        }
    }

    private LimitsCheckResult checkSchemaSize(ContentHandle content) {
        if (isLimitDisabled(TenantLimitsConfiguration::getMaxSchemaSizeBytes) || content == null) {
            return LimitsCheckResult.ok();
        }

        var size = content.getSizeBytes();
        if (size <= tenantContext.limitsConfig().getMaxSchemaSizeBytes()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, schema size is {} , max schema size is {}", size, tenantContext.limitsConfig().getMaxSchemaSizeBytes());
            return LimitsCheckResult.disallowed(MAX_SCHEMA_SIZE_EXCEEDED_MSG);
        }
    }

    public LimitsCheckResult canCreateArtifactVersion(String groupId, String artifactId, EditableArtifactMetaDataDto meta, ContentHandle content) {

        LimitsCheckResult mr = checkMetaData(meta);
        if (!mr.isAllowed()) {
            return mr;
        }

        LimitsCheckResult tsr = checkTotalSchemas();
        if (!tsr.isAllowed()) {
            return tsr;
        }

        LimitsCheckResult ssr = checkSchemaSize(content);
        if (!ssr.isAllowed()) {
            return ssr;
        }

        if (isLimitDisabled(TenantLimitsConfiguration::getMaxVersionsPerArtifactCount)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifactVersions = storageMetricsStore.getOrInitializeArtifactVersionsCounter(groupId, artifactId);

        if (currentArtifactVersions < tenantContext.limitsConfig().getMaxVersionsPerArtifactCount()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current versions per artifact for artifact {}/{} {} , max versions per artifacts allowed {}", groupId, artifactId, currentArtifactVersions, tenantContext.limitsConfig().getMaxVersionsPerArtifactCount());
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
        if (meta.getName() != null && isLimitEnabled(TenantLimitsConfiguration::getMaxArtifactNameLengthChars)) {
            if (meta.getName().length() > tenantContext.limitsConfig().getMaxArtifactNameLengthChars()) {
                errorMessages.add(MAX_NAME_LENGTH_EXCEEDED_MSG);
            }
        }

        //description is limited at db level to 1024 chars
        if (meta.getDescription() != null && isLimitEnabled(TenantLimitsConfiguration::getMaxArtifactDescriptionLengthChars)) {

            if (meta.getDescription().length() > tenantContext.limitsConfig().getMaxArtifactDescriptionLengthChars()) {
                errorMessages.add(MAX_DESC_LENGTH_EXCEEDED_MSG);
            }
        }

        if (meta.getLabels() != null) {
            if (isLimitEnabled(TenantLimitsConfiguration::getMaxArtifactLabelsCount) &&
                    meta.getLabels().size() > tenantContext.limitsConfig().getMaxArtifactLabelsCount()) {

                errorMessages.add(MAX_LABELS_EXCEEDED_MSG);

            } else if (isLimitEnabled(TenantLimitsConfiguration::getMaxLabelSizeBytes)) {

                meta.getLabels().forEach(l -> {

                    if (l.getBytes(StandardCharsets.UTF_8).length >
                            tenantContext.limitsConfig().getMaxLabelSizeBytes()) {
                        errorMessages.add(MAX_LABEL_SIZE_EXCEEDED_MSG);
                    }
                });
            }
        }

        if (meta.getProperties() != null) {
            if (isLimitEnabled(TenantLimitsConfiguration::getMaxArtifactPropertiesCount) &&
                    meta.getProperties().size() > tenantContext.limitsConfig().getMaxArtifactPropertiesCount()) {

                errorMessages.add(MAX_PROPERTIES_EXCEEDED_MSG);

            } else if (isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyKeySizeBytes) ||
                    isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyValueSizeBytes)){

                meta.getProperties().entrySet().forEach(e -> {

                    if (isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyKeySizeBytes) &&
                            e.getKey().length() > tenantContext.limitsConfig().getMaxPropertyKeySizeBytes()) {
                        errorMessages.add(MAX_PROP_KEY_SIZE_EXCEEDED_MSG);
                    }

                    if (isLimitEnabled(TenantLimitsConfiguration::getMaxPropertyValueSizeBytes) &&
                            e.getValue().length() > tenantContext.limitsConfig().getMaxPropertyValueSizeBytes()) {
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
