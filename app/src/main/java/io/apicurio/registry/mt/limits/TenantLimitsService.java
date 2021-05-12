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
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.metrics.StorageMetricsStore;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantLimitsService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

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
    TenantLimitsConfigurationService limitsConfiguration;

    @Inject
    StorageMetricsStore storageMetricsStore;

    private LimitsCheckResult checkTotalSchemas() {

        if (limitsConfiguration.getConfiguration().getMaxTotalSchemas() < 0) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentTotalSchemas = storageMetricsStore.getOrInitializeTotalSchemasCounter();

        if (currentTotalSchemas < limitsConfiguration.getConfiguration().getMaxTotalSchemas()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current total schemas {} , max total schemas {}", currentTotalSchemas, limitsConfiguration.getConfiguration().getMaxTotalSchemas());
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

        if (limitsConfiguration.getConfiguration().getMaxArtifacts() < 0) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifacts = storageMetricsStore.getOrInitializeArtifactsCounter();

        if (currentArtifacts < limitsConfiguration.getConfiguration().getMaxArtifacts()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current artifacts {} , max artifacts allowed {}", currentArtifacts, limitsConfiguration.getConfiguration().getMaxArtifacts());
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

        if (limitsConfiguration.getConfiguration().getMaxVersionsPerArtifact() < 0) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifactVersions = storageMetricsStore.getOrInitializeArtifactVersionsCounter(groupId, artifactId);

        if (currentArtifactVersions < limitsConfiguration.getConfiguration().getMaxVersionsPerArtifact()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current versions per artifact for artifact {}/{} {} , max versions per artifacts allowed {}", groupId, artifactId, currentArtifactVersions, limitsConfiguration.getConfiguration().getMaxVersionsPerArtifact());
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
        if (meta == null) {
            return LimitsCheckResult.ok();
        }
        List<String> errorMessages = new ArrayList<>();

        if (meta.getName() != null && limitsConfiguration.getConfiguration().getMaxNameLength() > 0) {
            if (meta.getName().length() > limitsConfiguration.getConfiguration().getMaxNameLength()) {
                errorMessages.add(MAX_NAME_LENGTH_EXCEEDED_MSG);
            }
        }

        if (meta.getDescription() != null && limitsConfiguration.getConfiguration().getMaxDescriptionLength() > 0) {
            if (meta.getDescription().length() > limitsConfiguration.getConfiguration().getMaxDescriptionLength()) {
                errorMessages.add(MAX_DESC_LENGTH_EXCEEDED_MSG);
            }
        }

        if (meta.getLabels() != null) {
            if (limitsConfiguration.getConfiguration().getMaxArtifactLabels() > 0 &&
                    meta.getLabels().size() > limitsConfiguration.getConfiguration().getMaxArtifactLabels()) {
                errorMessages.add(MAX_LABELS_EXCEEDED_MSG);

            } else if (limitsConfiguration.getConfiguration().getMaxLabelBytesSize() > 0) {

                meta.getLabels().forEach(l -> {

                    if (l.getBytes(StandardCharsets.UTF_8).length >
                            limitsConfiguration.getConfiguration().getMaxLabelBytesSize()) {
                        errorMessages.add(MAX_LABEL_SIZE_EXCEEDED_MSG);
                    }
                });
            }
        }

        if (meta.getProperties() != null) {
            if (limitsConfiguration.getConfiguration().getMaxArtifactProperties() > 0 &&
                    meta.getProperties().size() > limitsConfiguration.getConfiguration().getMaxArtifactProperties()) {
                errorMessages.add(MAX_PROPERTIES_EXCEEDED_MSG);

            } else if (limitsConfiguration.getConfiguration().getMaxPropertyKeyBytesSize() > 0 ||
                    limitsConfiguration.getConfiguration().getMaxPropertyValueBytesSize() > 0){

                meta.getProperties().entrySet().forEach(e -> {

                    if (limitsConfiguration.getConfiguration().getMaxPropertyKeyBytesSize() > 0 &&
                            e.getKey().length() > limitsConfiguration.getConfiguration().getMaxPropertyKeyBytesSize()) {
                        errorMessages.add(MAX_PROP_KEY_SIZE_EXCEEDED_MSG);
                    }

                    if (limitsConfiguration.getConfiguration().getMaxPropertyValueBytesSize() > 0 &&
                            e.getValue().length() > limitsConfiguration.getConfiguration().getMaxPropertyValueBytesSize()) {
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

}
