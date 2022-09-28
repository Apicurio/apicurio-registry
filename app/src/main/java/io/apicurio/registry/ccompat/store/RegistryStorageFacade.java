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

import java.util.List;
import java.util.function.Function;

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

/**
 *
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public interface RegistryStorageFacade {

    List<String> getSubjects(boolean deleted);

    List<SubjectVersion> getSubjectVersions(int contentId);

    /**
     * @return List of <b>schema versions</b> in the deleted subject
     */
    List<Integer> deleteSubject(String subject, boolean permanent) throws ArtifactNotFoundException, RegistryStorageException;


    /**
     * Create a new schema in the given subject.
     *
     * @return contentId
     */
    Long createSchema(String subject, String schema, String schemaType, List<SchemaReference> references, boolean normalize) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException;


    SchemaInfo getSchemaById(int contentId) throws RegistryStorageException;


    Schema getSchema(String subject, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;


    List<Integer> getVersions(String subject) throws ArtifactNotFoundException, RegistryStorageException;


    Schema getSchema(String subject, SchemaContent schema, boolean normalize) throws ArtifactNotFoundException, RegistryStorageException;


    /**
     * @return schema version
     *
     * @throws java.lang.IllegalArgumentException if the version string is not an int or "latest"
     */
    int deleteSchema(String subject, String version, boolean permanent) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;


    void createOrUpdateArtifactRule(String subject, RuleType type, RuleConfigurationDto dto);

    void createOrUpdateGlobalRule(RuleType type, RuleConfigurationDto dto);

    CompatibilityCheckResponse testCompatibilityBySubjectName(String subject,
            SchemaContent request, boolean verbose);

    CompatibilityCheckResponse testCompatibilityByVersion(String subject, String version,
                                                              SchemaContent request, boolean verbose);

    <T> T parseVersionString(String subject, String versionString, Function<String, T> then);

    RuleConfigurationDto getGlobalRule(RuleType ruleType);

    void deleteGlobalRule(RuleType ruleType);

    void deleteArtifactRule(String subject, RuleType ruleType);

    RuleConfigurationDto getArtifactRule(String subject, RuleType ruleType);

    List<Long> getContentIdsReferencingArtifact(String subject, String version);
}
