package io.apicurio.registry.limits;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.metrics.StorageMetricsStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Component that provides the logic to enforce the limits in the usage of the registry
 *
 */
@ApplicationScoped
public class RegistryLimitsService {

    //FIXME improve error messages
    private static final String MAX_TOTAL_SCHEMAS_EXCEEDED_MSG = "Maximum number of artifact versions exceeded";
    private static final String MAX_SCHEMA_SIZE_EXCEEDED_MSG = "Maximum size of artifact version exceeded";
    private static final String MAX_ARTIFACTS_EXCEEDED_MSG = "Maximum number of artifacts exceeded";
    private static final String MAX_VERSIONS_PER_ARTIFACT_EXCEEDED_MSG = "Maximum number of versions exceeded for this artifact";
    private static final String MAX_NAME_LENGTH_EXCEEDED_MSG = "Maximum artifact name length exceeded";
    private static final String MAX_DESC_LENGTH_EXCEEDED_MSG = "Maximum artifact description length exceeded";
    private static final String MAX_LABELS_EXCEEDED_MSG = "Maximum number of labels exceeded for this artifact";
    private static final String MAX_LABEL_KEY_SIZE_EXCEEDED_MSG = "Maximum label key size exceeded";
    private static final String MAX_LABEL_VALUE_SIZE_EXCEEDED_MSG = "Maximum label value size exceeded";

    @Inject
    Logger log;

    @Inject
    StorageMetricsStore storageMetricsStore;

    @Inject
    RegistryLimitsConfiguration registryLimitsConfiguration;

    private LimitsCheckResult checkTotalSchemas() {

        if (isLimitDisabled(RegistryLimitsConfiguration::getMaxTotalSchemasCount)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentTotalSchemas = storageMetricsStore.getOrInitializeTotalSchemasCounter();

        if (currentTotalSchemas < registryLimitsConfiguration.getMaxTotalSchemasCount()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current total schemas {} , max total schemas {}", currentTotalSchemas, registryLimitsConfiguration.getMaxTotalSchemasCount());
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

        if (isLimitDisabled(RegistryLimitsConfiguration::getMaxArtifactsCount)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifacts = storageMetricsStore.getOrInitializeArtifactsCounter();

        if (currentArtifacts < registryLimitsConfiguration.getMaxArtifactsCount()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current artifacts {} , max artifacts allowed {}", currentArtifacts, registryLimitsConfiguration.getMaxArtifactsCount());
            return LimitsCheckResult.disallowed(MAX_ARTIFACTS_EXCEEDED_MSG);
        }
    }

    private LimitsCheckResult checkSchemaSize(ContentHandle content) {
        if (isLimitDisabled(RegistryLimitsConfiguration::getMaxSchemaSizeBytes) || content == null) {
            return LimitsCheckResult.ok();
        }

        var size = content.getSizeBytes();
        if (size <= registryLimitsConfiguration.getMaxSchemaSizeBytes()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, schema size is {} , max schema size is {}", size, registryLimitsConfiguration.getMaxSchemaSizeBytes());
            return LimitsCheckResult.disallowed(MAX_SCHEMA_SIZE_EXCEEDED_MSG);
        }
    }

    public LimitsCheckResult canCreateArtifactVersion(String groupId, String artifactId, EditableVersionMetaDataDto meta, ContentHandle content) {

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

        if (isLimitDisabled(RegistryLimitsConfiguration::getMaxVersionsPerArtifactCount)) {
            //limits check disabled
            return LimitsCheckResult.ok();
        }

        long currentArtifactVersions = storageMetricsStore.getOrInitializeArtifactVersionsCounter(groupId, artifactId);

        if (currentArtifactVersions < registryLimitsConfiguration.getMaxVersionsPerArtifactCount()) {
            return LimitsCheckResult.ok();
        } else {
            log.debug("Limit reached, current versions per artifact for artifact {}/{} {} , max versions per artifacts allowed {}", groupId, artifactId, currentArtifactVersions, registryLimitsConfiguration.getMaxVersionsPerArtifactCount());
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
        if (meta == null || registryLimitsConfiguration == null) {
            return LimitsCheckResult.ok();
        }
        List<String> errorMessages = new ArrayList<>();

        checkName(meta.getName(), errorMessages);
        checkDescription(meta.getDescription(), errorMessages);
        checkLabels(meta.getLabels(), errorMessages);

        if (errorMessages.isEmpty()) {
            return LimitsCheckResult.ok();
        } else {
            return LimitsCheckResult.disallowed(String.join(", ", errorMessages));
        }
    }

    public LimitsCheckResult checkMetaData(EditableVersionMetaDataDto meta) {
        if (meta == null || registryLimitsConfiguration == null) {
            return LimitsCheckResult.ok();
        }
        List<String> errorMessages = new ArrayList<>();

        checkName(meta.getName(), errorMessages);
        checkDescription(meta.getDescription(), errorMessages);
        checkLabels(meta.getLabels(), errorMessages);

        if (errorMessages.isEmpty()) {
            return LimitsCheckResult.ok();
        } else {
            return LimitsCheckResult.disallowed(String.join(", ", errorMessages));
        }
    }

    protected void checkName(String name, List<String> errorMessages) {
        //name is limited at db level to 512 chars
        if (name != null && isLimitEnabled(RegistryLimitsConfiguration::getMaxArtifactNameLengthChars)) {
            if (name.length() > registryLimitsConfiguration.getMaxArtifactNameLengthChars()) {
                errorMessages.add(MAX_NAME_LENGTH_EXCEEDED_MSG);
            }
        }
    }

    protected void checkDescription(String description, List<String> errorMessages) {
        //description is limited at db level to 1024 chars
        if (description != null && isLimitEnabled(RegistryLimitsConfiguration::getMaxArtifactDescriptionLengthChars)) {

            if (description.length() > registryLimitsConfiguration.getMaxArtifactDescriptionLengthChars()) {
                errorMessages.add(MAX_DESC_LENGTH_EXCEEDED_MSG);
            }
        }
    }

    /**
     * @param meta
     * @param errorMessages
     */
    protected void checkLabels(Map<String, String> labels, List<String> errorMessages) {
        if (labels != null) {
            if (isLimitEnabled(RegistryLimitsConfiguration::getMaxArtifactPropertiesCount) &&
                    labels.size() > registryLimitsConfiguration.getMaxArtifactPropertiesCount()) {

                errorMessages.add(MAX_LABELS_EXCEEDED_MSG);

            } else if (isLimitEnabled(RegistryLimitsConfiguration::getMaxPropertyKeySizeBytes) ||
                    isLimitEnabled(RegistryLimitsConfiguration::getMaxPropertyValueSizeBytes)){

                labels.entrySet().forEach(e -> {

                    if (isLimitEnabled(RegistryLimitsConfiguration::getMaxPropertyKeySizeBytes) &&
                            e.getKey().length() > registryLimitsConfiguration.getMaxPropertyKeySizeBytes()) {
                        errorMessages.add(MAX_LABEL_KEY_SIZE_EXCEEDED_MSG);
                    }

                    if (isLimitEnabled(RegistryLimitsConfiguration::getMaxPropertyValueSizeBytes) &&
                            e.getValue().length() > registryLimitsConfiguration.getMaxPropertyValueSizeBytes()) {
                        errorMessages.add(MAX_LABEL_VALUE_SIZE_EXCEEDED_MSG);
                    }
                });
            }
        }
    }

    private boolean isLimitEnabled(Function<RegistryLimitsConfiguration, Long> limitGetter) {
        if (registryLimitsConfiguration != null) {
            Long limit = limitGetter.apply(registryLimitsConfiguration);
            if (limit != null && limit >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isLimitDisabled(Function<RegistryLimitsConfiguration, Long> limitGetter) {
        return !isLimitEnabled(limitGetter);
    }

}
