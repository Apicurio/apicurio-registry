/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
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
        return new ArrayList<>(storage.getArtifactIds());
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
                version -> FacadeConverter.convert(subject, storage.getArtifactVersion(subject, version)));
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
        // TODO -- handle deleted?
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(subject, ContentHandle.create(schema.getSchema()));
        StoredArtifact storedArtifact = storage.getArtifactVersion(subject, amd.getVersion());
        return FacadeConverter.convert(subject, storedArtifact);
    }

    @Override
    public CompletionStage<Long> createSchema(String subject, String schema) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {

        // TODO Should this creation and updating of an artifact be a different operation?
        // TODO method that returns a completion stage should not throw an exception
        CompletionStage<ArtifactMetaDataDto> artifactMeta = createOrUpdateArtifact(subject, schema);

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

    private CompletionStage<ArtifactMetaDataDto> createOrUpdateArtifact(String subject, String schema) {
        CompletionStage<ArtifactMetaDataDto> res;
        try {
            if (!doesArtifactExist(subject)) {
                rulesService.applyRules(subject, ArtifactType.AVRO, ContentHandle.create(schema), RuleApplicationType.CREATE);
                res = storage.createArtifact(subject, ArtifactType.AVRO, ContentHandle.create(schema))
                        .thenApply(r -> {
                            storage.createArtifactRule(subject, RuleType.VALIDITY,
                                    RuleConfigurationDto.builder().configuration(ValidityLevel.FULL.name()).build()
                            );
                            return r;
                        });
            } else {
                rulesService.applyRules(subject, ArtifactType.AVRO, ContentHandle.create(schema), RuleApplicationType.UPDATE);
                res = storage.updateArtifact(subject, ArtifactType.AVRO, ContentHandle.create(schema));
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
