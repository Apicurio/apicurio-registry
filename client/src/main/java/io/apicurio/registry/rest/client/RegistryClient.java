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

import io.apicurio.registry.rest.v2.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.io.InputStream;
import java.util.List;

/**
 * @author Carles Arnal <carnalca@redhat.com>
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

	void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data);

	void deleteArtifactVersionMetaData(String groupId, String artifactId, String version);

	void updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data);

	VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset, Integer limit);

	VersionMetaData createArtifactVersion(String groupId, String artifactId, String xRegistryVersion, InputStream data);

	ArtifactSearchResults listArtifactsInGroup(String groupId, Integer limit, Integer offset, SortOrder order, SortBy orderby);

	ArtifactMetaData createArtifact(String groupId, ArtifactType xRegistryArtifactType, String xRegistryArtifactId, String xRegistryVersion, IfExists ifExists, Boolean canonical, InputStream data);

	void deleteArtifactsInGroup(String groupId);

	InputStream getContentById(long contentId);

	InputStream getContentByGlobalId(long globalId);

    default InputStream getContentByHash(String contentHash) {
        return getContentByHash(contentHash, null);
    };
	InputStream getContentByHash(String contentHash, Boolean canonical);

	ArtifactSearchResults searchArtifacts(String name, Integer offset, Integer limit, SortOrder order, SortBy orderby, List<String> labels, List<String> properties, String description, String artifactgroup);

	ArtifactSearchResults searchArtifactsByContent(Integer offset, Integer limit, SortOrder order, SortBy orderby, InputStream data);
}
