/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest.client;


import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 * <p>
 * Rest client compatible with the Registry API V2
 */
public interface RegistryClient {

	InputStream getLatestArtifact(String groupId, String artifactId);

	ArtifactMetaData updateArtifact(String groupId, String artifactId, InputStream data);

	void deleteArtifact(String groupId, String artifactId);

	ArtifactMetaData getArtifactMetaData(String groupId, String artifactId);

	void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data);

	VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data);

	default VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, InputStream data) {
        return getArtifactVersionMetaDataByContent(groupId, artifactId, null, data);
    }

	List<RuleType> listArtifactRules(String groupId, String artifactId);

	void createArtifactRule(String groupId, String artifactId, Rule data);

	void deleteArtifactRules(String groupId, String artifactId);

	Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule);

	Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data);

	void deleteArtifactRule(String groupId, String artifactId, RuleType rule);

	void updateArtifactState(String groupId, String artifactId, UpdateState data);

	void testUpdateArtifact(String groupId, String artifactId, InputStream data);

	InputStream getArtifactVersion(String groupId, String artifactId, String version);

	VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version);

	void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
	                                   EditableMetaData data);

	void deleteArtifactVersionMetaData(String groupId, String artifactId, String version);

	void updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data);

	VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset,
	                                          Integer limit);

	VersionMetaData createArtifactVersion(String groupId, String artifactId, String version, InputStream data);

	ArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order, Integer offset, Integer limit);

	default ArtifactSearchResults listArtifactsInGroup(String groupId) {
        return listArtifactsInGroup(groupId, null, null, null, null);
    }

	ArtifactMetaData createArtifact(String groupId, String artifactId, String version, ArtifactType artifactType, IfExists ifExists, Boolean canonical, InputStream data);

    default ArtifactMetaData createArtifact(String groupId, String artifactId, InputStream data) {
        return createArtifact(groupId, artifactId, null, null, null, null, data);
    }
    default ArtifactMetaData createArtifact(String groupId, String artifactId, String version, InputStream data) {
        return createArtifact(groupId, artifactId, version, null, null, null, data);
    }
    default ArtifactMetaData createArtifact(String groupId, String artifactId, ArtifactType artifactType, InputStream data) {
        return createArtifact(groupId, artifactId, null, artifactType, null, null, data);
    }
    default ArtifactMetaData createArtifact(String groupId, String artifactId, ArtifactType artifactType, IfExists ifExists, InputStream data) {
        return createArtifact(groupId, artifactId, null, artifactType, ifExists, null, data);
    }

	void deleteArtifactsInGroup(String groupId);

	InputStream getContentById(long contentId);

	InputStream getContentByGlobalId(long globalId);

    InputStream getContentByHash(String contentHash, Boolean canonical);

    default InputStream getContentByHash(String contentHash) {
        return getContentByHash(contentHash, null);
    };

	ArtifactSearchResults searchArtifacts(String group, String name, String description, List<String> labels,
	        List<String> properties, SortBy orderBy, SortOrder order, Integer offset, Integer limit);

	ArtifactSearchResults searchArtifactsByContent(InputStream data, SortBy orderBy, SortOrder order, Integer offset, Integer limit);

	List<RuleType> listGlobalRules();

	void createGlobalRule(Rule data);

	void deleteAllGlobalRules();

	Rule getGlobalRuleConfig(RuleType rule);

	Rule updateGlobalRuleConfig(RuleType rule, Rule data);

	void deleteGlobalRule(RuleType rule);

	List<NamedLogConfiguration> listLogConfigurations();

	NamedLogConfiguration getLogConfiguration(String logger);

	NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data);

	NamedLogConfiguration removeLogConfiguration(String logger);

	InputStream exportData();

	void importData(InputStream data);

	void setNextRequestHeaders(Map<String, String> requestHeaders);

	Map<String, String> getHeaders();
}
