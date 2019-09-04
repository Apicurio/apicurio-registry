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

package io.apicurio.registry.storage;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletionStage;


/**
 * The storage layer for the registry.
 * @author eric.wittmann@gmail.com
 */
public interface RegistryStorage {

    /**
     * Creates a new artifact (from the given value) in the storage.  The artifactId must be globally unique
     * within the storage layer.  Returns a map of meta-data generated by the storage layer, such as the 
     * generated, globally unique versionId of the new value.
     * @param artifactId
     * @param artifactType
     * @param content
     * @throws ArtifactAlreadyExistsException
     * @throws RegistryStorageException
     */
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, String content)
            throws ArtifactAlreadyExistsException, RegistryStorageException;
    
    /**
     * Deletes an artifact by its unique id. Returns list of artifacts versions.
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    /**
     * Gets the most recent version of the value of the artifact with the given ID.
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Updates the artifact value by storing the given value as a new version of the artifact.  Previous value
     * is NOT overwitten.  Returns a map of meta-data generated by the storage layer, such as the generated,
     * globally unique versionId of the new value.
     * @param artifactId
     * @param artifactType
     * @param content
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Get all artifact ids.
     * @return all artifact ids
     */
    public Set<String> getArtifactIds(/* TODO -- filter? */);

    /**
     * Gets the stored meta-data for an artifact by ID.  This will include client-editable meta-data such as 
     * name and description, but also generated meta-data such as "modifedOn" and "versionId".
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    /**
     * Updates the stored meta-data for an artifact by ID.  Only the client-editable meta-data can be updated.  Client
     * editable meta-data includes e.g. name and description. TODO what if set to null?
     * @param artifactId
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException;
    
    /**
     * Gets a list of rules configured for a specific Artifact (by ID).  This will return only the names of the
     * rules.
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public List<RuleType> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    /**
     * Creates an artifact rule for a specific Artifact.  If the named rule already exists for the artifact, then
     * this should fail.
     * @param artifactId
     * @param rule
     * @param config
     * @throws ArtifactNotFoundException
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     */
    public void createArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException;

    /**
     * Deletes all rules stored/configured for the artifact.
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets all of the information for a single rule configured on a given artifact.
     * @param artifactId
     * @param rule
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Updates the configuration information for a single rule on a given artifact.
     * @param artifactId
     * @param rule
     * @param config
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;
    
    /**
     * Deletes a single stored/configured rule for a given artifact.
     * @param artifactId
     * @param rule
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    public void deleteArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;
    
    /**
     * Gets a sorted set of all artifact versions that exist for a given artifact.
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the most recent version of the content of the artifact with the given global ID.
     * @param id
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the stored value for a single version of a given artifact.
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Deletes a single version of a given artifact.
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Gets the stored meta-data for a single version of an artifact.  This will return all meta-data for the
     * version, including any user edited meta-data along with anything generated by the storage.
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    /**
     * Updates the user-editable meta-data for a single version of a given artifact.  Only the client-editable 
     * meta-data can be updated.  Client editable meta-data includes e.g. name and description.
     * @param artifactId
     * @param version
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    /**
     * Deletes the user-editable meta-data for a singel version of a given artifact.  Only the client-editable
     * meta-data is deleted.  Any meta-data generated by the storage is preserved.
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    /**
     * Gets a list of all global rule names.
     * @throws RegistryStorageException
     */
    public List<RuleType> getGlobalRules() throws RegistryStorageException;

    /**
     * Creates a single global rule.  Duplicates (by name) are not allowed.  Stores the rule name and configuration.
     * @param rule
     * @param config
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     */
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException;
    
    /**
     * Deletes all of the globally configured rules.
     * @throws RegistryStorageException
     */
    public void deleteGlobalRules() throws RegistryStorageException;
    
    /**
     * Gets all information about a single global rule.
     * @param rule
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException;

    /**
     * Updates the configuration settings for a single global rule.
     * @param rule
     * @param config
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException;

    /**
     * Deletes a single global rule.
     * @param rule
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException;
}
