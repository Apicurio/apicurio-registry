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

import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.io.InputStream;
import java.util.List;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public interface RegistryRestClient extends AutoCloseable {

    List<String> listArtifacts();

    
    ArtifactMetaData createArtifact(InputStream data);


    ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, InputStream data);

    
    ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, InputStream data, IfExistsType ifExists, Boolean canonical);


    InputStream getLatestArtifact(String artifactId);


    ArtifactMetaData updateArtifact(String artifactId, ArtifactType artifactType, InputStream data);


    void deleteArtifact(String artifactId);


    void updateArtifactState(String artifactId, UpdateState newState);


    ArtifactMetaData getArtifactMetaData(String artifactId);


    void updateArtifactMetaData(String artifactId, EditableMetaData metaData);


    ArtifactMetaData getArtifactMetaDataByContent(String artifactId, Boolean canonical, InputStream data);


    List<Long> listArtifactVersions(String artifactId);


    VersionMetaData createArtifactVersion(String artifactId, ArtifactType artifactType, InputStream data);


    InputStream getArtifactVersion(String artifactId, Integer version);


    void updateArtifactVersionState(String artifactId, Integer version, UpdateState newState);


    VersionMetaData getArtifactVersionMetaData(String artifactId, Integer version);


    void updateArtifactVersionMetaData(String artifactId, Integer version, EditableMetaData metaData);


    void deleteArtifactVersionMetaData(String artifactId, Integer version);


    List<RuleType> listArtifactRules(String artifactId);


    void createArtifactRule(String artifactId, Rule ruleConfig);


    void deleteArtifactRules(String artifactId);


    Rule getArtifactRuleConfig(String artifactId, RuleType ruleType);


    Rule updateArtifactRuleConfig(String artifactId, RuleType ruleType, Rule ruleConfig);


    void deleteArtifactRule(String artifactId, RuleType ruleType);


    void testUpdateArtifact(String artifactId, ArtifactType artifactType, InputStream data);


    InputStream getArtifactByGlobalId(long globalId);


    ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId);


    Rule getGlobalRuleConfig(RuleType ruleType);


    Rule updateGlobalRuleConfig(RuleType ruleType, Rule data);


    void deleteGlobalRule(RuleType ruleType);


    List<RuleType> listGlobalRules();


    void createGlobalRule(Rule data);


    void deleteAllGlobalRules();


    ArtifactSearchResults searchArtifacts(String search, SearchOver over, SortOrder order, Integer offset, Integer limit);


    VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit);

}
