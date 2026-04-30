package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.impl.polling.model.Type;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import io.apicurio.registry.storage.impl.polling.model.v0.Version;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Validates loaded data against configured rules before the blue-green swap.
 * <p>
 * For each artifact, the validator determines which versions need checking
 * based on the {@code validatedUpTo} field:
 * <ul>
 *   <li>If {@code validatedUpTo} is not set, only the last version is validated
 *       against its predecessor.</li>
 *   <li>If set, consecutive version pairs after the specified version are validated.</li>
 * </ul>
 * <p>
 * Rule resolution (artifact → group → global → defaults) is delegated to
 * {@link RulesService}, using the inactive storage that was just loaded.
 */
public class PollingDataValidator {

    private static final Logger log = LoggerFactory.getLogger(PollingDataValidator.class);

    private final RulesService rulesService;

    public PollingDataValidator(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    /**
     * Validates all loaded artifacts against their configured rules.
     *
     * @param state the processing state with indexed files
     * @param storage the inactive storage containing the loaded data
     * @param pollingConfig the polling storage configuration
     */
    public void validate(ProcessingState state, RegistryStorage storage, PollingStorageConfig pollingConfig) {
        for (PollingDataFile file : state.fromTypeIndex(Type.ARTIFACT)) {
            Artifact artifact = file.getEntityUnchecked();

            // Skip artifacts not matching this registry
            List<String> registryIds = artifact.getRegistryIds();
            if (registryIds != null && !registryIds.isEmpty()
                    && !registryIds.contains(pollingConfig.getRegistryId())) {
                continue;
            }

            List<Version> versions = artifact.getVersions();
            if (versions == null || versions.isEmpty()) {
                continue;
            }

            validateArtifactVersions(state, file, artifact, storage);
        }
    }

    private void validateArtifactVersions(ProcessingState state, PollingDataFile file,
                                          Artifact artifact, RegistryStorage storage) {
        List<Version> versions = artifact.getVersions();

        // Determine which versions need validation
        int startIndex = determineValidationStart(versions, artifact.getValidatedUpTo());
        if (startIndex >= versions.size()) {
            return; // Nothing to validate
        }

        // Build existing content incrementally — each version is validated against
        // all preceding versions, not against everything in storage (which already
        // contains all versions from the import step).
        List<TypedContent> existingContent = new ArrayList<>();

        // Collect content for versions before the validation start point
        for (int i = 0; i < startIndex; i++) {
            StoredArtifactVersionDto storedVersion = loadStoredVersion(artifact, versions.get(i), storage);
            if (storedVersion != null) {
                existingContent.add(toTypedContent(storedVersion));
            }
        }

        for (int i = startIndex; i < versions.size(); i++) {
            Version current = versions.get(i);

            StoredArtifactVersionDto storedVersion = loadStoredVersion(artifact, current, storage);
            if (storedVersion == null) {
                state.recordError(file, "Could not load version '%s' from storage during validation",
                        current.getVersion());
                return;
            }

            TypedContent currentContent = toTypedContent(storedVersion);

            // Load references for integrity rule validation
            List<ArtifactReferenceDto> refDtos = storedVersion.getReferences();
            List<ArtifactReference> references;
            Map<String, TypedContent> resolvedReferences;
            if (refDtos != null && !refDtos.isEmpty()) {
                references = refDtos.stream()
                        .map(dto -> {
                            var ref = new ArtifactReference();
                            ref.setGroupId(dto.getGroupId());
                            ref.setArtifactId(dto.getArtifactId());
                            ref.setVersion(dto.getVersion());
                            ref.setName(dto.getName());
                            return ref;
                        })
                        .toList();
                resolvedReferences = RegistryContentUtils.recursivelyResolveReferences(
                        refDtos, storage::getContentByReference);
            } else {
                references = Collections.emptyList();
                resolvedReferences = Collections.emptyMap();
            }

            try {
                rulesService.applyRules(storage,
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getArtifactType(),
                        currentContent, existingContent,
                        references, resolvedReferences);
            } catch (RuleViolationException e) {
                state.recordError(file, "Rule %s violation for version '%s': %s",
                        e.getRuleType(), current.getVersion(), e.getMessage());
            }

            existingContent.add(currentContent);
        }
    }

    /**
     * Determines the index from which to start validation.
     * <ul>
     *   <li>If validatedUpTo is null: validate only the last version (return versions.size() - 1)</li>
     *   <li>If validatedUpTo matches a version: start after that version</li>
     *   <li>If validatedUpTo doesn't match: validate from the beginning</li>
     * </ul>
     */
    private int determineValidationStart(List<Version> versions, String validatedUpTo) {
        if (validatedUpTo == null) {
            // Default: only validate the last version
            return versions.size() - 1;
        }

        for (int i = 0; i < versions.size(); i++) {
            if (validatedUpTo.equals(versions.get(i).getVersion())) {
                return i + 1; // Start after the validated version
            }
        }

        // validatedUpTo doesn't match any version — validate from the beginning
        log.warn("validatedUpTo '{}' does not match any version, validating all versions", validatedUpTo);
        return 0;
    }

    private static TypedContent toTypedContent(StoredArtifactVersionDto storedVersion) {
        String contentType = storedVersion.getContentType() != null
                ? storedVersion.getContentType()
                : ContentTypeUtil.determineContentType(storedVersion.getContent());
        return TypedContent.create(storedVersion.getContent(), contentType);
    }

    /**
     * Loads the stored version (content + references) from storage.
     */
    private StoredArtifactVersionDto loadStoredVersion(Artifact artifact, Version version,
                                                       RegistryStorage storage) {
        try {
            return storage.getArtifactVersionContent(
                    artifact.getGroupId(), artifact.getArtifactId(), version.getVersion());
        } catch (Exception e) {
            log.debug("Could not load content for {}:{}:{}: {}",
                    artifact.getGroupId(), artifact.getArtifactId(),
                    version.getVersion(), e.getMessage());
            return null;
        }
    }
}
