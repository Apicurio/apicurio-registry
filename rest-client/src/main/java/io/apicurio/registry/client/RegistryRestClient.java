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
package io.apicurio.registry.client;


import io.apicurio.registry.client.exception.ArtifactAlreadyExistsException;
import io.apicurio.registry.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.client.exception.RestClientException;
import io.apicurio.registry.client.exception.RuleAlreadyExistsException;
import io.apicurio.registry.client.exception.RuleNotFoundException;
import io.apicurio.registry.client.exception.VersionNotFoundException;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.IfExistsType;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.io.InputStream;
import java.util.List;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public interface RegistryRestClient extends AutoCloseable {

    List<String> listArtifacts();


    ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, IfExistsType ifExists, InputStream data) throws ArtifactAlreadyExistsException, RestClientException;


    InputStream getLatestArtifact(String artifactId) throws ArtifactNotFoundException, RestClientException;


    ArtifactMetaData updateArtifact(String artifactId, ArtifactType artifactType, InputStream data) throws ArtifactNotFoundException, RestClientException;


    void deleteArtifact(String artifactId) throws ArtifactNotFoundException, RestClientException;


    void updateArtifactState(String artifactId, UpdateState newState) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    ArtifactMetaData getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RestClientException;


    void updateArtifactMetaData(String artifactId, EditableMetaData metaData) throws ArtifactNotFoundException, RestClientException;


    ArtifactMetaData getArtifactMetaDataByContent(String artifactId, InputStream data) throws ArtifactNotFoundException, RestClientException;


    List<Long> listArtifactVersions(String artifactId) throws ArtifactNotFoundException, RestClientException;


    VersionMetaData createArtifactVersion(String artifactId, ArtifactType artifactType, InputStream data) throws ArtifactNotFoundException, RestClientException;


    InputStream getArtifactVersion(String artifactId, Integer version) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    void updateArtifactVersionState(String artifactId, Integer version, UpdateState newState) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    VersionMetaData getArtifactVersionMetaData(String artifactId, Integer version) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    void updateArtifactVersionMetaData(String artifactId, Integer version, EditableMetaData metaData) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    void deleteArtifactVersionMetaData(String artifactId, Integer version) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    List<RuleType> listArtifactRules(String artifactId) throws ArtifactNotFoundException;


    void createArtifactRule(String artifactId, Rule ruleConfig) throws ArtifactNotFoundException, RuleAlreadyExistsException, RestClientException;


    void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RestClientException;


    Rule getArtifactRuleConfig(String artifactId, RuleType ruleType) throws ArtifactNotFoundException, RuleNotFoundException, RestClientException;


    Rule updateArtifactRuleConfig(String artifactId, RuleType ruleType, Rule ruleConfig) throws ArtifactNotFoundException, RuleNotFoundException, RestClientException;


    void deleteArtifactRule(String artifactId, RuleType ruleType) throws ArtifactNotFoundException, RuleNotFoundException, RestClientException;


    void testUpdateArtifact(String artifactId, ArtifactType artifactType, InputStream data);


    InputStream getArtifactByGlobalId(long globalId) throws ArtifactNotFoundException, RestClientException;


    ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) throws ArtifactNotFoundException, RestClientException;


    Rule getGlobalRuleConfig(RuleType ruleType) throws RuleNotFoundException, RestClientException;


    Rule updateGlobalRuleConfig(RuleType ruleType, Rule data) throws RuleNotFoundException, RestClientException;


    void deleteGlobalRule(RuleType ruleType) throws RuleNotFoundException, RestClientException;


    List<RuleType> listGlobalRules() throws RestClientException;


    void createGlobalRule(Rule data) throws RuleAlreadyExistsException;


    void deleteAllGlobalRules() throws RestClientException;


    ArtifactSearchResults searchArtifacts(String search, SearchOver over, SortOrder order, Integer offset, Integer limit);


    VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit);

}
