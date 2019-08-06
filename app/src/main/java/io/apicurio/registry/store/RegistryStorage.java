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

package io.apicurio.registry.store;

import java.util.List;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;

/**
 * The storage layer for the registry.
 * @author eric.wittmann@gmail.com
 */
public interface RegistryStorage {

    public void createArtifact(String artifactId, String content) throws ArtifactAlreadyExistsException, RegistryStorageException;
    
    public void deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    public void updateArtifact(String artifactId, String content) throws ArtifactNotFoundException, RegistryStorageException;
    
    public ArtifactMetaData getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    public void updateArtifactMetaData(String artifactId, EditableMetaData metaData) throws ArtifactNotFoundException, RegistryStorageException;
    
    public List<Rule> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    public void createArtifactRule(String artifactId, Rule rule) throws ArtifactNotFoundException, ArtifactRuleAlreadyExistsException, RegistryStorageException;
    
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    public Rule getArtifactRule(String artifactId, String ruleName) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;
    
    public void deleteArtifactRule(String artifactId, String ruleNam) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;
    
    public List<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException;
    
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    public VersionMetaData getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableMetaData metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
    
    public List<Rule> getGlobalRules() throws RegistryStorageException;
    
    public void createGlobalRule(Rule rule) throws RegistryStorageException;
    
    public void deleteGlobalRules() throws RegistryStorageException;
    
    public Rule getGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException;

    public void updateGlobalRule(Rule rule) throws RuleNotFoundException, RegistryStorageException;

    public void deleteGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException;

}
