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

import io.apicurio.registry.ccompat.dto.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Ales Justin
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
public class RegistryStorageFacadeImpl implements RegistryStorageFacade {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    public List<String> getSubjects() {
        // TODO maybe not necessary...
        return new ArrayList<>(storage.getArtifactIds(null));
    }

    @Override
    public List<SubjectVersion> getSubjectVersions(int globalId) {

        final String artifactId = storage.getArtifactMetaData(globalId).getId();

        return storage.getArtifactVersions(artifactId)
                .stream()
                .map(version -> FacadeConverter.convert(artifactId, version))
                .collect(Collectors.toList());
    }

    @Override
    public List<Integer> deleteSubject(String subject) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.deleteArtifact(subject)
                .stream()
                .map(FacadeConverter::convertUnsigned)
                .collect(Collectors.toList());
    }

    @Override
    public SchemaContent getSchemaContent(int globalId) throws ArtifactNotFoundException, RegistryStorageException {
        return FacadeConverter.convert(storage.getArtifactVersion(globalId));
        // TODO StoredArtifact should contain artifactId IF we are not treating globalId separately
    }

    @Override
    public Schema getSchema(String subject, String versionString) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return parseVersionString(subject, versionString,
                version -> {
                    if (ArtifactState.DISABLED.equals(storage.getArtifactVersionMetaData(subject, version).getState())) {
                        throw new VersionNotFoundException(subject, version);
                    }
                    return FacadeConverter.convert(subject, storage.getArtifactVersion(subject, version));
                });
    }

    @Override
    public List<Integer> getVersions(String subject) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersions(subject)
                .stream()
                .map(FacadeConverter::convertUnsigned)
                .collect(Collectors.toList());
    }

    @Override
    public Schema getSchema(String subject, SchemaContent schema) throws ArtifactNotFoundException, RegistryStorageException {
        // Don't canonicalize the content when getting it - Confluent does not.
        ArtifactVersionMetaDataDto amd = storage.getArtifactVersionMetaData(subject, false, ContentHandle.create(schema.getSchema()));
        StoredArtifact storedArtifact = storage.getArtifactVersion(subject, amd.getVersion());
        return FacadeConverter.convert(subject, storedArtifact);
    }

    @Override
    public CompletionStage<Long> createSchema(String subject, String schema, String schemaType) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        // Check to see if this content is already registered - return the global ID of that content
        // if it exists.  If not, then register the new content.
        try {
            ContentHandle content = ContentHandle.create(schema);
            // Don't canonicalize the content when getting it - Confluent does not.
            ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(subject, false, content);
            return CompletableFuture.completedFuture(dto.getGlobalId());
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - when it happens just move on and create
        }
        
        // TODO Should this creation and updating of an artifact be a different operation?
        // TODO method that returns a completion stage should not throw an exception
        CompletionStage<ArtifactMetaDataDto> artifactMeta = createOrUpdateArtifact(subject, schema, ArtifactType.fromValue(schemaType));

        return artifactMeta.thenApply(ArtifactMetaDataDto::getGlobalId);
    }

    @Override
    public int deleteSchema(String subject, String versionString) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return FacadeConverter.convertUnsigned(parseVersionString(subject, versionString, version -> {
            storage.deleteArtifactVersion(subject, version);
            return version;
        }));
    }

    @Override
    public void createOrUpdateArtifactRule(String subject, RuleType type, RuleConfigurationDto dto) {
        if (!doesArtifactRuleExist(subject, RuleType.COMPATIBILITY)) {
            storage.createArtifactRule(subject, RuleType.COMPATIBILITY, dto);
        } else {
            storage.updateArtifactRule(subject, RuleType.COMPATIBILITY, dto);
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
    public CompatibilityCheckResponse testCompatibilityBySubjectName(String subject, String version,
            SchemaContent request) {

        return parseVersionString(subject, version, parsedVersion -> {

            try {
                final ArtifactVersionMetaDataDto artifact = storage
                        .getArtifactVersionMetaData(subject, parsedVersion);
                rulesService.applyRules(subject, parsedVersion, artifact.getType(),
                        ContentHandle.create(request.getSchema()));
                return CompatibilityCheckResponse.IS_COMPATIBLE;
            } catch (RuleViolationException ex) {
                return CompatibilityCheckResponse.IS_NOT_COMPATIBLE;
            }
        });
    }

    private CompletionStage<ArtifactMetaDataDto> createOrUpdateArtifact(String subject, String schema, ArtifactType artifactType) {
        CompletionStage<ArtifactMetaDataDto> res;
        try {
            if (!doesArtifactExist(subject)) {
                rulesService.applyRules(subject, artifactType, ContentHandle.create(schema), RuleApplicationType.CREATE);
                res = storage.createArtifact(subject, artifactType, ContentHandle.create(schema));
            } else {
                rulesService.applyRules(subject, artifactType, ContentHandle.create(schema), RuleApplicationType.UPDATE);
                res = storage.updateArtifact(subject, artifactType, ContentHandle.create(schema));
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
     * - if it's an <b>integer</b>, use that;
     * - if it's a string "latest", find out and use the subject's (artifact's) latest version;
     * - otherwise throw an IllegalArgumentException.
     * On success, call the "then" function with the parsed version (MUST NOT be null) and return it's result.
     * Optionally provide an "else" function that will receive the exception that would be otherwise thrown.
     */
    public <T> T parseVersionString(String subject, String versionString, Function<Long, T> then) {
        long version;
        if ("latest".equals(versionString)) {
            SortedSet<Long> versions = storage.getArtifactVersions(subject);
            version = versions.last();
        } else {
            try {
                version = Integer.parseUnsignedInt(versionString); // required by the spec, ignoring the possible leading "+"
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Illegal version format: " + versionString, e);
            }
        }
        return then.apply(version);
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
    public void deleteArtifactRule(String subject, RuleType ruleType) {
        storage.deleteArtifactRule(subject, ruleType);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String subject, RuleType ruleType) {
        return storage.getArtifactRule(subject, ruleType);
    }


    private boolean doesArtifactExist(String artifactId) {
        try {
            storage.getArtifact(artifactId);
            return true;
        } catch (ArtifactNotFoundException ignored) {
            return false;
        }
    }

    private boolean doesArtifactRuleExist(String artifactId, RuleType type) {
        try {
            storage.getArtifactRule(artifactId, type);
            return true;
        } catch (RuleNotFoundException ignored) {
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
}
