/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.ccompat.store;

import io.apicurio.registry.ccompat.dto.*;
import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import io.apicurio.registry.storage.*;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.VersionUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.DEFAULT;

/**
 * @author Ales Justin
 * @author Jakub Senko <em>m@jsenko.net</em>
 * @author Carles Arnal
 */
@ApplicationScoped
public class RegistryStorageFacadeImpl implements RegistryStorageFacade {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Inject
    FacadeConverter converter;

    @Inject
    CCompatConfig cconfig;

    @Inject
    Logger log;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Override
    public List<String> getSubjects(boolean deleted, String groupId) {
        return storage.searchArtifacts(Set.of(SearchFilter.ofGroup(groupId)), OrderBy.createdOn, OrderDirection.asc, 0, cconfig.maxSubjects.get())
                .getArtifacts()
                .stream()
                .filter(searchedArtifactDto -> isCcompatManagedType(searchedArtifactDto.getType()) && shouldFilterState(deleted, searchedArtifactDto.getState()))
                .map(SearchedArtifactDto::getId).collect(Collectors.toList());
    }

    private boolean shouldFilterState(boolean deleted, ArtifactState state) {
        if (deleted) {
            //if deleted is enabled, just return all states
            return true;
        } else {
            return state.equals(ArtifactState.ENABLED);
        }
    }

    @Override
    public List<SubjectVersion> getSubjectVersions(int contentId) {
        if (cconfig.legacyIdModeEnabled.get()) {
            ArtifactMetaDataDto artifactMetaData = storage.getArtifactMetaData(contentId);
            return Collections.singletonList(converter.convert(artifactMetaData.getId(), artifactMetaData.getVersionId()));
        }

        return storage.getArtifactVersionsByContentId(contentId)
                .stream()
                .map(artifactMetaData -> converter.convert(artifactMetaData.getId(), artifactMetaData.getVersionId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Integer> deleteSubject(String subject, boolean permanent, String groupId) throws ArtifactNotFoundException, RegistryStorageException {
        if (permanent) {
            return storage.deleteArtifact(groupId, subject)
                    .stream()
                    .map(VersionUtil::toInteger)
                    .map(converter::convertUnsigned)
                    .collect(Collectors.toList());
        } else {
            storage.updateArtifactState(groupId, subject, ArtifactState.DISABLED);

            return storage.getArtifactVersions(groupId, subject)
                    .stream()
                    .map(VersionUtil::toLong)
                    .map(converter::convertUnsigned)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @Override
    public SchemaInfo getSchemaById(int contentId) throws ArtifactNotFoundException, RegistryStorageException {
        ContentHandle contentHandle;
        List<ArtifactReferenceDto> references;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactDto artifactVersion = storage.getArtifactVersion(contentId);
            contentHandle = artifactVersion.getContent();
            references = artifactVersion.getReferences();
        } else {
            ContentWrapperDto contentWrapper = storage.getArtifactByContentId(contentId);
            contentHandle = storage.getArtifactByContentId(contentId).getContent();
            references = contentWrapper.getReferences();
            List<ArtifactMetaDataDto> artifacts = storage.getArtifactVersionsByContentId(contentId);
            if (artifacts == null || artifacts.isEmpty()) {
                //the contentId points to an orphaned content
                throw new ArtifactNotFoundException("ContentId: " + contentId);
            }
        }
        return converter.convert(contentHandle, ArtifactTypeUtil.determineArtifactType(contentHandle, null, null, storage.resolveReferences(references), factory.getAllArtifactTypes()), references);
    }

    @Override
    public Schema getSchema(String subject, String versionString, String groupId) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return parseVersionString(subject, versionString, groupId,
                version -> {
                    ArtifactVersionMetaDataDto artifactVersionMetaDataDto = storage.getArtifactVersionMetaData(groupId, subject, version);
                    StoredArtifactDto storedArtifact = storage.getArtifactVersion(groupId, subject, version);
                    return converter.convert(subject, storedArtifact, artifactVersionMetaDataDto.getType());
                });
    }

    @Override
    public List<Integer> getVersions(String subject, String groupId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersions(groupId, subject)
                .stream()
                .map(VersionUtil::toLong)
                .map(converter::convertUnsigned)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public Schema getSchemaNormalize(String subject, SchemaInfo schema, boolean normalize, String groupId) throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactVersionMetaDataDto amd;

        final List<ArtifactReferenceDto> artifactReferenceDtos = parseReferences(schema.getReferences(), groupId);

        if (cconfig.canonicalHashModeEnabled.get() || normalize) {
            amd = storage.getArtifactVersionMetaData(groupId, subject, true, ContentHandle.create(schema.getSchema()), artifactReferenceDtos);
        } else {
            amd = storage.getArtifactVersionMetaData(groupId, subject, false, ContentHandle.create(schema.getSchema()), artifactReferenceDtos);
        }
        StoredArtifactDto storedArtifact = storage.getArtifactVersion(groupId, subject, amd.getVersion());
        return converter.convert(subject, storedArtifact);
    }

    @Override
    public Long createSchema(String subject, String schema, String schemaType, List<SchemaReference> references, boolean normalize, String groupId) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        // Check to see if this content is already registered - return the global ID of that content
        // if it exists.  If not, then register the new content.
        if (null == schema) {
            throw new UnprocessableEntityException("The schema provided is null.");
        }

        final List<ArtifactReferenceDto> artifactReferences = parseReferences(references, groupId);

        try {
            ContentHandle content = ContentHandle.create(schema);
            ArtifactVersionMetaDataDto dto;
            if (cconfig.canonicalHashModeEnabled.get() || normalize) {
                dto = storage.getArtifactVersionMetaData(groupId, subject, true, content, artifactReferences);
            } else {
                dto = storage.getArtifactVersionMetaData(groupId, subject, false, content, artifactReferences);
            }
            return cconfig.legacyIdModeEnabled.get() ? dto.getGlobalId() : dto.getContentId();
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - when it happens just move on and create
        }

        // We validate the schema at creation time by inferring the type from the content
        try {
            Map<String, ContentHandle> resolvedReferences = resolveReferences(references);

            final String artifactType = ArtifactTypeUtil.determineArtifactType(ContentHandle.create(schema), null, null, resolvedReferences, factory.getAllArtifactTypes());
            if (schemaType != null && !artifactType.equals(schemaType)) {
                throw new UnprocessableEntityException(String.format("Given schema is not from type: %s", schemaType));
            }
            ArtifactMetaDataDto artifactMeta = createOrUpdateArtifact(subject, schema, artifactType, references, normalize, groupId);
            return cconfig.legacyIdModeEnabled.get() ? artifactMeta.getGlobalId() : artifactMeta.getContentId();
        } catch (InvalidArtifactTypeException ex) {
            //If no artifact type can be inferred, throw invalid schema ex
            throw new UnprocessableEntityException(ex.getMessage());
        }
    }

    private Map<String, ContentHandle> resolveReferences(List<SchemaReference> references) {
        Map<String, ContentHandle> resolvedReferences = Collections.emptyMap();
        if (references != null && !references.isEmpty()) {
            //Transform the given references into dtos and set the contentId, this will also detect if any of the passed references does not exist.
            final List<ArtifactReferenceDto> referencesAsDtos = references.stream()
                    .map(schemaReference -> {
                        final ArtifactReferenceDto artifactReferenceDto = new ArtifactReferenceDto();
                        artifactReferenceDto.setArtifactId(schemaReference.getSubject());
                        artifactReferenceDto.setVersion(String.valueOf(schemaReference.getVersion()));
                        artifactReferenceDto.setName(schemaReference.getName());
                        artifactReferenceDto.setGroupId(null);
                        return artifactReferenceDto;
                    })
                    .collect(Collectors.toList());

            resolvedReferences = storage.resolveReferences(referencesAsDtos);
        }
        return resolvedReferences;
    }

    @Override
    public int deleteSchema(String subject, String versionString, boolean permanent, String groupId) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return VersionUtil.toInteger(parseVersionString(subject, versionString, groupId, version -> {
            if (permanent) {
                storage.deleteArtifactVersion(groupId, subject, version);
            } else {
                storage.updateArtifactState(groupId, subject, version, ArtifactState.DISABLED);
            }
            return version;
        }));
    }

    @Override
    public void createOrUpdateArtifactRule(String subject, RuleType type, RuleConfigurationDto dto, String groupId) {
        if (!doesArtifactRuleExist(subject, RuleType.COMPATIBILITY, groupId)) {
            storage.createArtifactRule(groupId, subject, RuleType.COMPATIBILITY, dto);
        } else {
            storage.updateArtifactRule(groupId, subject, RuleType.COMPATIBILITY, dto);
        }
    }

    @Override
    public void createOrUpdateGlobalRule(RuleType type, RuleConfigurationDto dto) {
        if (!doesGlobalRuleExist(RuleType.COMPATIBILITY)) {
            storage.createGlobalRule(RuleType.COMPATIBILITY, dto);
        } else {
            storage.updateGlobalRule(RuleType.COMPATIBILITY, dto);
        }
    }

    @Override
    public CompatibilityCheckResponse testCompatibilityByVersion(String subject, String version,
                                                                 SchemaContent request, boolean verbose, String groupId) {
        return parseVersionString(subject, version, groupId, v -> {
            try {
                final ArtifactVersionMetaDataDto artifact = storage
                        .getArtifactVersionMetaData(groupId, subject, v);
                rulesService.applyRules(groupId, subject, v, artifact.getType(),
                        ContentHandle.create(request.getSchema()), Collections.emptyList(), Collections.emptyMap());
                return CompatibilityCheckResponse.IS_COMPATIBLE;
            } catch (RuleViolationException ex) {
                if (verbose) {
                    return new CompatibilityCheckResponse(false, ex.getMessage());
                } else {
                    return CompatibilityCheckResponse.IS_NOT_COMPATIBLE;
                }
            } catch (UnprocessableSchemaException ex) {
                throw new UnprocessableEntityException(ex.getMessage(), ex);
            }
        });
    }

    @Override
    public CompatibilityCheckResponse testCompatibilityBySubjectName(String subject,
                                                                     SchemaContent request, boolean verbose, String groupId) {
        try {
            final List<String> versions = storage
                    .getArtifactVersions(groupId, subject);
            for (String version : versions) {
                final ArtifactVersionMetaDataDto artifactVersionMetaData = storage.getArtifactVersionMetaData(groupId, subject, version);
                rulesService.applyRules(groupId, subject, version, artifactVersionMetaData.getType(),
                        ContentHandle.create(request.getSchema()), Collections.emptyList(), Collections.emptyMap());
            }
            return CompatibilityCheckResponse.IS_COMPATIBLE;
        } catch (RuleViolationException ex) {
            if (verbose) {
                return new CompatibilityCheckResponse(false, ex.getMessage());
            } else {
                return CompatibilityCheckResponse.IS_NOT_COMPATIBLE;
            }
        } catch (UnprocessableSchemaException ex) {
            throw new UnprocessableEntityException(ex.getMessage(), ex);
        }
    }

    private ArtifactMetaDataDto createOrUpdateArtifact(String subject, String schema, String artifactType, List<SchemaReference> references, boolean normalize, String groupId) {
        ArtifactMetaDataDto res;
        final List<ArtifactReferenceDto> parsedReferences = parseReferences(references, groupId);
        final List<ArtifactReference> artifactReferences = parsedReferences.stream().map(dto -> {
            return ArtifactReference.builder()
                    .name(dto.getName())
                    .groupId(dto.getGroupId())
                    .artifactId(dto.getArtifactId())
                    .version(dto.getVersion())
                    .build();
        }).collect(Collectors.toList());
        final Map<String, ContentHandle> resolvedReferences = storage.resolveReferences(parsedReferences);
        try {
            ContentHandle schemaContent;
            if (normalize) {
                schemaContent = this.canonicalizeContent(artifactType, ContentHandle.create(schema), references);
            } else {
                schemaContent = ContentHandle.create(schema);
            }
            if (!doesArtifactExist(subject, groupId)) {
                rulesService.applyRules(groupId, subject, artifactType, schemaContent, RuleApplicationType.CREATE,
                        artifactReferences, resolvedReferences);
                res = storage.createArtifact(groupId, subject, null, artifactType, schemaContent, parsedReferences);
            } else {
                rulesService.applyRules(groupId, subject, artifactType, schemaContent, RuleApplicationType.UPDATE,
                        artifactReferences, resolvedReferences);
                res = storage.updateArtifact(groupId, subject, null, artifactType, schemaContent, parsedReferences);
            }
        } catch (RuleViolationException ex) {
            if (ex.getRuleType() == RuleType.VALIDITY) {
                throw new UnprocessableEntityException(ex.getMessage(), ex);
            } else {
                throw new ConflictException(ex.getMessage(), ex);
            }
        }
        return res;
    }

    /**
     * Given a version string:
     * - if it's a <b>non-negative integer</b>, use that;
     * - if it's a string "latest", find out and use the subject's (artifact's) latest version;
     * - if it's <b>-1</b>, do the same as "latest", even though this behavior is undocumented.
     * See https://github.com/Apicurio/apicurio-registry/issues/2851
     * - otherwise throw an IllegalArgumentException.
     * On success, call the "then" function with the parsed version (MUST NOT be null) and return it's result.
     * Optionally provide an "else" function that will receive the exception that would be otherwise thrown.
     */
    @Override
    public <T> T parseVersionString(String subject, String versionString, String groupId, Function<String, T> then) {
        String version;
        if ("latest".equals(versionString)) {
            version = getLatestArtifactVersionForSubject(subject, groupId);
        } else {
            try {
                var numericVersion = Integer.parseInt(versionString);
                if (numericVersion >= 0) {
                    version = versionString;
                } else if (numericVersion == -1) {
                    version = getLatestArtifactVersionForSubject(subject, groupId);
                } else {
                    throw new ArtifactNotFoundException("Illegal version format: " + versionString);
                }
            } catch (NumberFormatException e) {
                throw new ArtifactNotFoundException("Illegal version format: " + versionString);
            }
        }
        return then.apply(version);
    }

    private String getLatestArtifactVersionForSubject(String subject, String groupId) {
        try {
            ArtifactMetaDataDto latest = storage.getArtifactMetaData(groupId, subject);
            return latest.getVersion();
        } catch (ArtifactNotFoundException ex) {
            throw new VersionNotFoundException(groupId, subject, "latest");
        }
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType ruleType) {
        return storage.getGlobalRule(ruleType);
    }

    @Override
    public void deleteGlobalRule(RuleType ruleType) {
        storage.deleteGlobalRule(ruleType);
    }

    @Override
    public void deleteArtifactRule(String subject, RuleType ruleType, String groupId) {
        try {
            storage.deleteArtifactRule(groupId, subject, ruleType);
        } catch (RuleNotFoundException e) {
            //Ignore, fail only when the artifact is not found
        }
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String subject, RuleType ruleType, String groupId) {
        return storage.getArtifactRule(groupId, subject, ruleType);
    }

    @Override
    public List<Long> getContentIdsReferencingArtifact(String subject, String versionString, String groupId) {
        if (cconfig.legacyIdModeEnabled.get()) {
            return parseVersionString(subject, versionString, groupId,
                    version -> storage.getGlobalIdsReferencingArtifact(groupId, subject, version));
        }

        return parseVersionString(subject, versionString, groupId,
                version -> storage.getContentIdsReferencingArtifact(groupId, subject, version));
    }

    private boolean doesArtifactExist(String artifactId, String groupId) {
        try {
            storage.getArtifact(groupId, artifactId, DEFAULT);
            return true;
        } catch (ArtifactNotFoundException ignored) {
            return false;
        }
    }

    private boolean doesArtifactRuleExist(String artifactId, RuleType type, String groupId) {
        try {
            storage.getArtifactRule(groupId, artifactId, type);
            return true;
        } catch (RuleNotFoundException | ArtifactNotFoundException ignored) {
            return false;
        }
    }

    private boolean doesGlobalRuleExist(RuleType type) {
        try {
            storage.getGlobalRule(type);
            return true;
        } catch (RuleNotFoundException ignored) {
            return false;
        }
    }

    //Parse references and resolve the contentId. This will fail with ArtifactNotFound if a reference cannot be found.
    private List<ArtifactReferenceDto> parseReferences(List<SchemaReference> references, String groupId) {
        if (references != null) {
            return references.stream()
                    .map(schemaReference -> {
                        // Try to get the artifact version.  This will fail if not found with ArtifactNotFound or VersionNotFound
                        storage.getArtifactVersionMetaData(groupId, schemaReference.getSubject(), String.valueOf(schemaReference.getVersion()));
                        return new ArtifactReferenceDto(groupId, schemaReference.getSubject(), String.valueOf(schemaReference.getVersion()), schemaReference.getName());
                    }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private boolean isCcompatManagedType(String artifactType) {
        return artifactType.equals(ArtifactType.AVRO) || artifactType.equals(ArtifactType.PROTOBUF) || artifactType.equals(ArtifactType.JSON);
    }

    /**
     * Canonicalize the given content, returns the content unchanged in the case of an error.
     *
     * @param artifactType
     * @param content
     */
    private ContentHandle canonicalizeContent(String artifactType, ContentHandle content, List<SchemaReference> references) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            return canonicalizer.canonicalize(content, resolveReferences(references));
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType);
            return content;
        }
    }
}
