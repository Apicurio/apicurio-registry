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
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SchemaReference;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import java.util.List;
import java.util.function.Function;

/**
 * @author Ales Justin
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public interface RegistryStorageFacade {

    default List<String> getSubjects(boolean deleted) {
        return getSubjects(deleted, null);
    }

    List<SubjectVersion> getSubjectVersions(int contentId);

    /**
     * @return List of <b>schema versions</b> in the deleted subject
     */
    default List<Integer> deleteSubject(String subject, boolean permanent) throws ArtifactNotFoundException, RegistryStorageException {
        return deleteSubject(subject, permanent, null);
    }

    /**
     * Create a new schema in the given subject.
     *
     * @return contentId
     */
    default Long createSchema(String subject, String schema, String schemaType, List<SchemaReference> references, boolean normalize) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        return createSchema(subject, schema, schemaType, references, normalize, null);
    }


    SchemaInfo getSchemaById(int contentId) throws RegistryStorageException;


    default Schema getSchema(String subject, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return getSchema(subject, version, null);
    }

    default List<Integer> getVersions(String subject) throws ArtifactNotFoundException, RegistryStorageException {
        return getVersions(subject, null);
    }

    default Schema getSchemaNormalize(String subject, SchemaInfo schema, boolean normalize) throws ArtifactNotFoundException, RegistryStorageException {
        return getSchemaNormalize(subject, schema, normalize, null);
    }


    /**
     * @return schema version
     * @throws java.lang.IllegalArgumentException if the version string is not an int or "latest"
     */
    default int deleteSchema(String subject, String version, boolean permanent) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return deleteSchema(subject, version, permanent, null);
    }


    default void createOrUpdateArtifactRule(String subject, RuleType type, RuleConfigurationDto dto) {
        createOrUpdateArtifactRule(subject, type, dto, null);
    }

    void createOrUpdateGlobalRule(RuleType type, RuleConfigurationDto dto);

    default CompatibilityCheckResponse testCompatibilityBySubjectName(String subject,
                                                                      SchemaContent request, boolean verbose) {
        return testCompatibilityBySubjectName(subject, request, verbose, null);
    }

    default CompatibilityCheckResponse testCompatibilityByVersion(String subject, String version,
                                                                  SchemaContent request, boolean verbose) {
        return testCompatibilityByVersion(subject, version, request, verbose, null);
    }

    <T> T parseVersionString(String subject, String versionString, String groupId, Function<String, T> then);

    RuleConfigurationDto getGlobalRule(RuleType ruleType);

    void deleteGlobalRule(RuleType ruleType);

    default void deleteArtifactRule(String subject, RuleType ruleType) {
        deleteArtifactRule(subject, ruleType, null);
    }

    default RuleConfigurationDto getArtifactRule(String subject, RuleType ruleType) {
        return getArtifactRule(subject, ruleType, null);
    }

    default List<Long> getContentIdsReferencingArtifact(String subject, String version) {
        return getContentIdsReferencingArtifact(subject, version, null);
    }

    CompatibilityCheckResponse testCompatibilityBySubjectName(String subject, SchemaContent request, boolean fverbose, String groupId);

    CompatibilityCheckResponse testCompatibilityByVersion(String subject, String version, SchemaContent request, boolean fverbose, String groupId);

    void createOrUpdateArtifactRule(String subject, RuleType compatibility, RuleConfigurationDto dto, String groupId);

    void deleteArtifactRule(String subject, RuleType compatibility, String groupId);

    RuleConfigurationDto getArtifactRule(String subject, RuleType compatibility, String groupId);

    int deleteSchema(String subject, String version, boolean fnormalize, String groupId);

    Schema getSchema(String subject, String version, String groupId);

    List<Long> getContentIdsReferencingArtifact(String subject, String version, String groupId);

    List<String> getSubjects(boolean fdeleted, String groupId);

    Schema getSchemaNormalize(String subject, SchemaInfo request, boolean fnormalize, String groupId);

    List<Integer> deleteSubject(String subject, boolean fpermanent, String groupId);

    List<Integer> getVersions(String subject, String groupId);

    Long createSchema(String subject, String schema, String schemaType, List<SchemaReference> references, boolean fnormalize, String groupId);
}
