/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (Map<String, String> headers, the "License");
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
import java.util.Map;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public interface RegistryRestClient extends AutoCloseable {

    List<String> listArtifacts(Map<String, String> headers);


    ArtifactMetaData createArtifact(Map<String, String> headers, InputStream data) throws ArtifactAlreadyExistsException, RestClientException;

    ArtifactMetaData createArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data) throws ArtifactAlreadyExistsException, RestClientException;


    ArtifactMetaData createArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data, IfExistsType ifExists, Boolean canonical) throws ArtifactAlreadyExistsException, RestClientException;


    InputStream getLatestArtifact(Map<String, String> headers, String artifactId);


    ArtifactMetaData updateArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data) throws ArtifactNotFoundException, RestClientException;


    void deleteArtifact(Map<String, String> headers, String artifactId) throws ArtifactNotFoundException, RestClientException;


    void updateArtifactState(Map<String, String> headers, String artifactId, UpdateState newState) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    ArtifactMetaData getArtifactMetaData(Map<String, String> headers, String artifactId) throws ArtifactNotFoundException, RestClientException;


    void updateArtifactMetaData(Map<String, String> headers, String artifactId, EditableMetaData metaData) throws ArtifactNotFoundException, RestClientException;


    ArtifactMetaData getArtifactMetaDataByContent(Map<String, String> headers, String artifactId, Boolean canonical, InputStream data) throws ArtifactNotFoundException, RestClientException;


    List<Long> listArtifactVersions(Map<String, String> headers, String artifactId) throws ArtifactNotFoundException, RestClientException;


    VersionMetaData createArtifactVersion(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data) throws ArtifactNotFoundException, RestClientException;


    InputStream getArtifactVersion(Map<String, String> headers, String artifactId, Integer version) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    void updateArtifactVersionState(Map<String, String> headers, String artifactId, Integer version, UpdateState newState) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    VersionMetaData getArtifactVersionMetaData(Map<String, String> headers, String artifactId, Integer version) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    void updateArtifactVersionMetaData(Map<String, String> headers, String artifactId, Integer version, EditableMetaData metaData) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    void deleteArtifactVersionMetaData(Map<String, String> headers, String artifactId, Integer version) throws ArtifactNotFoundException, VersionNotFoundException, RestClientException;


    List<RuleType> listArtifactRules(Map<String, String> headers, String artifactId) throws ArtifactNotFoundException;


    void createArtifactRule(Map<String, String> headers, String artifactId, Rule ruleConfig) throws ArtifactNotFoundException, RuleAlreadyExistsException, RestClientException;


    void deleteArtifactRules(Map<String, String> headers, String artifactId) throws ArtifactNotFoundException, RestClientException;


    Rule getArtifactRuleConfig(Map<String, String> headers, String artifactId, RuleType ruleType) throws ArtifactNotFoundException, RuleNotFoundException, RestClientException;


    Rule updateArtifactRuleConfig(Map<String, String> headers, String artifactId, RuleType ruleType, Rule ruleConfig) throws ArtifactNotFoundException, RuleNotFoundException, RestClientException;


    void deleteArtifactRule(Map<String, String> headers, String artifactId, RuleType ruleType) throws ArtifactNotFoundException, RuleNotFoundException, RestClientException;


    void testUpdateArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data);


    InputStream getArtifactByGlobalId(Map<String, String> headers, long globalId) throws ArtifactNotFoundException, RestClientException;


    ArtifactMetaData getArtifactMetaDataByGlobalId(Map<String, String> headers, long globalId) throws ArtifactNotFoundException, RestClientException;


    Rule getGlobalRuleConfig(Map<String, String> headers, RuleType ruleType) throws RuleNotFoundException, RestClientException;


    Rule updateGlobalRuleConfig(Map<String, String> headers, RuleType ruleType, Rule data) throws RuleNotFoundException, RestClientException;


    void deleteGlobalRule(Map<String, String> headers, RuleType ruleType) throws RuleNotFoundException, RestClientException;


    List<RuleType> listGlobalRules(Map<String, String> headers) throws RestClientException;


    void createGlobalRule(Map<String, String> headers, Rule data) throws RuleAlreadyExistsException;


    void deleteAllGlobalRules(Map<String, String> headers) throws RestClientException;


    ArtifactSearchResults searchArtifacts(Map<String, String> headers, String search, SearchOver over, SortOrder order, Integer offset, Integer limit);


    VersionSearchResults searchVersions(Map<String, String> headers, String artifactId, Integer offset, Integer limit);

}
