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

package io.apicurio.registry.rest.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.ArtifactAlreadyExistsException;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.client.request.JsonBodyHandler;
import io.apicurio.registry.rest.client.request.RequestHandler;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_BASE_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_METADATA;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_RULE;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_RULES;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_STATE;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_TEST;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_VERSION;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_VERSIONS;
import static io.apicurio.registry.rest.client.impl.Routes.GROUP_BASE_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.SEARCH_ARTIFACTS;
import static io.apicurio.registry.rest.client.impl.Routes.VERSION_METADATA;
import static io.apicurio.registry.rest.client.impl.Routes.VERSION_STATE;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RegistryClientImpl implements RegistryClient {

	private static final Map<String, String> EMPTY_QUERY_PARAMS = Collections.emptyMap();
	private static final Map<String, String> EMPTY_REQUEST_HEADERS = Collections.emptyMap();

	private final RequestHandler requestHandler;
	private final ObjectMapper mapper;

	public RegistryClientImpl(String endpoint) {
		requestHandler = new RequestHandler(endpoint);
		mapper = new ObjectMapper();
	}

	@Override
	public InputStream getLatestArtifact(String groupId, String artifactId) {

		return requestHandler
				.sendGetRequest(ARTIFACT_BASE_PATH, EMPTY_QUERY_PARAMS, BodyHandlers.ofInputStream(), groupId,
						artifactId);
	}

	@Override
	public ArtifactMetaData updateArtifact(String groupId, String artifactId, InputStream data) {

		return requestHandler.sendPutRequest(ARTIFACT_BASE_PATH, EMPTY_QUERY_PARAMS,
				new JsonBodyHandler<>(ArtifactMetaData.class), data, groupId, artifactId).get();
	}

	@Override
	public void deleteArtifact(String groupId, String artifactId) {

		requestHandler
				.sendDeleteRequest(ARTIFACT_BASE_PATH, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
						groupId, artifactId);
	}

	@Override
	public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {

		return requestHandler.sendGetRequest(ARTIFACT_METADATA, EMPTY_QUERY_PARAMS,
				new JsonBodyHandler<>(ArtifactMetaData.class), groupId, artifactId).get();
	}

	@Override
	public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {

		try {

			requestHandler
					.sendPutRequest(ARTIFACT_METADATA, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
							new ByteArrayInputStream(mapper.writeValueAsBytes(data)), groupId, artifactId);

		} catch (IOException e) {
			throw parseError(e);
		}
	}

	@Override
	public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId,
	                                                           Boolean canonical, InputStream data) {

		return requestHandler.sendPostRequest(VERSION_METADATA, EMPTY_REQUEST_HEADERS,
				Map.of(Parameters.CANONICAL, String.valueOf(canonical)),
				new JsonBodyHandler<>(VersionMetaData.class), data, groupId, artifactId).get();
	}

	@Override
	public List<RuleType> listArtifactRules(String groupId, String artifactId) {

		//FIXME proper handling of list results
		return requestHandler
				.sendGetRequest(ARTIFACT_RULES, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(List.class),
						groupId, artifactId).get();
	}

	@Override
	public void createArtifactRule(String groupId, String artifactId, Rule data) {

		try {
			requestHandler.sendPostRequest(ARTIFACT_RULES, EMPTY_REQUEST_HEADERS, EMPTY_QUERY_PARAMS,
					new JsonBodyHandler<>(Void.class),
					new ByteArrayInputStream(mapper.writeValueAsBytes(data)), groupId, artifactId);
		} catch (JsonProcessingException e) {
			throw parseError(e);
		}
	}

	@Override
	public void deleteArtifactRules(String groupId, String artifactId) {

		requestHandler
				.sendDeleteRequest(ARTIFACT_RULES, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
						groupId, artifactId);
	}

	@Override
	public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {

		return requestHandler
				.sendGetRequest(ARTIFACT_RULE, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Rule.class), groupId,
						rule.value()).get();
	}

	@Override
	public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule,
	                                     Rule data) {

		try {
			return requestHandler
					.sendPutRequest(ARTIFACT_RULE, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Rule.class),
							new ByteArrayInputStream(mapper.writeValueAsBytes(data)), groupId, artifactId,
							rule.value()).get();
		} catch (JsonProcessingException e) {
			throw parseError(e);
		}
	}

	@Override
	public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {

		requestHandler.sendDeleteRequest(ARTIFACT_RULE, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
				groupId, artifactId, rule.value());
	}

	@Override
	public void updateArtifactState(String groupId, String artifactId, UpdateState data) {

		try {
			requestHandler
					.sendPutRequest(ARTIFACT_STATE, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
							new ByteArrayInputStream(mapper.writeValueAsBytes(data)), groupId, artifactId);
		} catch (JsonProcessingException e) {
			throw parseError(e);
		}
	}

	@Override
	public void testUpdateArtifact(String groupId, String artifactId, InputStream data) {

		try {
			requestHandler
					.sendPutRequest(ARTIFACT_TEST, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
							new ByteArrayInputStream(mapper.writeValueAsBytes(data)), groupId, artifactId);
		} catch (JsonProcessingException e) {
			throw parseError(e);
		}
	}

	@Override
	public InputStream getArtifactVersion(String groupId, String artifactId, String version) {

		return requestHandler
				.sendGetRequest(ARTIFACT_VERSION, EMPTY_QUERY_PARAMS, BodyHandlers.ofInputStream(), groupId,
						version);
	}

	@Override
	public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId,
	                                                  String version) {

		return requestHandler.sendGetRequest(VERSION_METADATA, EMPTY_QUERY_PARAMS,
				new JsonBodyHandler<>(VersionMetaData.class), groupId, version).get();
	}

	@Override
	public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
	                                          EditableMetaData data) {

		try {
			requestHandler
					.sendPutRequest(VERSION_METADATA, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
							new ByteArrayInputStream(mapper.writeValueAsBytes(data)), groupId, artifactId);
		} catch (JsonProcessingException e) {
			throw parseError(e);
		}
	}

	@Override
	public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {

		requestHandler
				.sendDeleteRequest(VERSION_METADATA, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
						groupId, artifactId, version);
	}

	@Override
	public void updateArtifactVersionState(String groupId, String artifactId, String version,
	                                       UpdateState data) {

		try {
			requestHandler
					.sendPutRequest(VERSION_STATE, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
							new ByteArrayInputStream(mapper.writeValueAsBytes(data)), groupId, artifactId,
							version);
		} catch (JsonProcessingException e) {
			throw parseError(e);
		}
	}

	@Override
	public VersionSearchResults listArtifactVersions(String groupId, String artifactId,
	                                                 Integer offset, Integer limit) {

		return requestHandler.sendGetRequest(ARTIFACT_VERSIONS,
				Map.of(Parameters.LIMIT, String.valueOf(limit), Parameters.OFFSET, String.valueOf(offset)),
				new JsonBodyHandler<>(VersionSearchResults.class), groupId, artifactId).get();
	}

	@Override
	public VersionMetaData createArtifactVersion(String groupId, String artifactId,
	                                             String xRegistryVersion, InputStream data) {

		return requestHandler.sendPostRequest(ARTIFACT_VERSION, Map.of(Headers.VERSION, xRegistryVersion),
				EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(VersionMetaData.class), data, groupId, artifactId)
				.get();
	}

	@Override
	public ArtifactSearchResults listArtifactsInGroup(String groupId, Integer limit, Integer offset,
	                                                  SortOrder order, SortBy orderby) {

		final Map<String, String> queryParams = new HashMap<>();

		checkCommonQueryParams(offset, limit, order, orderby, queryParams);

		return requestHandler.sendGetRequest(GROUP_BASE_PATH, queryParams,
				new JsonBodyHandler<>(ArtifactSearchResults.class), groupId).get();
	}

	@Override
	public ArtifactSearchResults listArtifactsInGroup(String groupId) {

		return this.listArtifactsInGroup(groupId, null, null, null, null);
	}

	@Override
	public ArtifactMetaData createArtifact(InputStream data)
			throws ArtifactAlreadyExistsException, RestClientException {
		return this.createArtifact(null, null, null, data);
	}

	@Override
	public ArtifactMetaData createArtifact(String groupId, ArtifactType artifactType,
	                                       String artifactId, InputStream data) throws ArtifactAlreadyExistsException, RestClientException {
		return this.createArtifact(groupId, artifactType, artifactId, null, null, data);
	}

	@Override
	public ArtifactMetaData createArtifact(String groupId, ArtifactType artifactType,
	                                       String artifactId, IfExists ifExists, Boolean canonical, InputStream data) {
		return this.createArtifact(groupId, artifactType, artifactId, null, ifExists, canonical, data);
	}

	@Override
	public ArtifactMetaData createArtifact(String groupId, ArtifactType artifactType,
	                                       String artifactId, String version, IfExists ifExists, Boolean canonical, InputStream data) {

		final Map<String, String> queryParams = new HashMap<>(
				Map.of(Headers.ARTIFACT_ID, artifactId, Headers.ARTIFACT_TYPE, artifactType.value()));

		if (version != null) {
			queryParams.put(Headers.VERSION, version);
		}

		return requestHandler.sendPostRequest(GROUP_BASE_PATH, queryParams,
				Map.of(Parameters.CANONICAL, String.valueOf(canonical)),
				new JsonBodyHandler<>(ArtifactMetaData.class), data, groupId).get();
	}

	@Override
	public void deleteArtifactsInGroup(String groupId) {

		requestHandler
				.sendDeleteRequest(GROUP_BASE_PATH, EMPTY_QUERY_PARAMS, new JsonBodyHandler<>(Void.class),
						groupId);

	}

	@Override
	public InputStream getContentById(long contentId) {
		return requestHandler
				.sendGetRequest(Routes.IDS_CONTENT_ID, EMPTY_QUERY_PARAMS, BodyHandlers.ofInputStream(),
						String.valueOf(contentId));
	}

	@Override
	public InputStream getContentByGlobalId(long globalId) {
		return requestHandler
				.sendGetRequest(Routes.IDS_GLOBAL_ID, EMPTY_QUERY_PARAMS, BodyHandlers.ofInputStream(),
						String.valueOf(globalId));
	}

	@Override
	public InputStream getContentByHash(String contentHash, Boolean canonical) {
		Map<String, String> queryParams = EMPTY_QUERY_PARAMS;
		if (canonical != null && canonical) {
			queryParams = Map.of(Parameters.CANONICAL, String.valueOf(canonical));
		}
		return requestHandler
				.sendGetRequest(Routes.IDS_CONTENT_HASH, queryParams, BodyHandlers.ofInputStream(),
						contentHash);
	}

	@Override
	public ArtifactSearchResults searchArtifacts(String name, Integer offset, Integer limit,
	                                             SortOrder order, SortBy orderby, List<String> labels, List<String> properties, String description,
	                                             String artifactgroup) {

		try {

			final Map<String, String> queryParams = new HashMap<>();

			if (name != null) {
				queryParams.put(Parameters.NAME, name);
			}

			if (description != null) {
				queryParams.put(Parameters.DESCRIPTION, description);
			}

			if (artifactgroup != null) {
				queryParams.put(Parameters.GROUP, artifactgroup);
			}

			checkCommonQueryParams(offset, limit, order, orderby, queryParams);

			if (labels != null && !labels.isEmpty()) {
				queryParams.put(Parameters.LABELS, mapper.writeValueAsString(labels));
			}

			if (properties != null && !properties.isEmpty()) {
				queryParams.put(Parameters.PROPERTIES, mapper.writeValueAsString(properties));
			}

			return requestHandler.sendGetRequest(SEARCH_ARTIFACTS, queryParams,
					new JsonBodyHandler<>(ArtifactSearchResults.class)).get();

		} catch (JsonProcessingException e) {
			throw parseError(e);
		}
	}

	@Override
	public ArtifactSearchResults searchArtifactsByContent(Integer offset, Integer limit,
	                                                      SortOrder order, SortBy orderby, InputStream data) {
		return null;
	}

	private void checkCommonQueryParams(Integer offset, Integer limit, SortOrder order, SortBy orderby,
	                                    Map<String, String> queryParams) {

		if (offset != null) {
			queryParams.put(Parameters.OFFSET, String.valueOf(offset));
		}

		if (limit != null) {
			queryParams.put(Parameters.LIMIT, String.valueOf(limit));
		}

		if (order != null) {
			queryParams.put(Parameters.SORT_ORDER, order.value());
		}

		if (orderby != null) {
			queryParams.put(Parameters.ORDER_BY, orderby.value());
		}
	}

	private RestClientException parseError(Exception ex) {

		//FIXME proper error handling
		return new RestClientException(new Error());
	}
}