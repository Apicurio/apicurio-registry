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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.v1.beans.Error;
import io.apicurio.registry.rest.v2.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

import static java.net.http.HttpResponse.BodyHandlers;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RegistryClientImpl implements RegistryClient {

	private HttpClient client;
	private ObjectMapper mapper;
	private String endpoint;


	public RegistryClientImpl(String endpoint) {

		if (!endpoint.endsWith("/")) {
			endpoint += "/";
		}

		final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();

		this.endpoint = endpoint;
		this.client = httpClientBuilder.build();
		this.mapper = new ObjectMapper();
	}


	@Override
	public InputStream getLatestArtifact(String groupId, String artifactId) {

		try {

			final HttpRequest req = HttpRequest.newBuilder()
					.uri(buildURI(endpoint + Routes.ARTIFACTS_BASE_PATH, Collections.emptyList(), groupId, artifactId))
					.GET()
					.build();

			final HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());

			if (200 == res.statusCode()) {
				return res.body();
			}
		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
		return null;
	}

	@Override
	public ArtifactMetaData updateArtifact(String groupId, String artifactId, InputStream data) {
		return null;
	}

	@Override
	public void deleteArtifact(String groupId, String artifactId) {

		try {
			final HttpRequest req = HttpRequest.newBuilder()
					.uri(buildURI(endpoint + Routes.ARTIFACTS_BASE_PATH, Collections.emptyList(), groupId, artifactId))
					.DELETE()
					.build();

			client.send(req, BodyHandlers.ofInputStream());

		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
	}

	@Override
	public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
		return null;
	}

	@Override
	public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {

	}

	@Override
	public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data) {
		return null;
	}

	@Override
	public List<RuleType> listArtifactRules(String groupId, String artifactId) {
		return null;
	}

	@Override
	public void createArtifactRule(String groupId, String artifactId, Rule data) {

	}

	@Override
	public void deleteArtifactRules(String groupId, String artifactId) {

	}

	@Override
	public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
		return null;
	}

	@Override
	public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
		return null;
	}

	@Override
	public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {

	}

	@Override
	public void updateArtifactState(String groupId, String artifactId, UpdateState data) {

	}

	@Override
	public void testUpdateArtifact(String groupId, String artifactId, InputStream data) {

	}

	@Override
	public InputStream getArtifactVersion(String groupId, String artifactId, String version) {
		return null;
	}

	@Override
	public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
		return null;
	}

	@Override
	public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data) {

	}

	@Override
	public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {

	}

	@Override
	public void updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data) {

	}

	@Override
	public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset, Integer limit) {
		return null;
	}

	@Override
	public VersionMetaData createArtifactVersion(String groupId, String artifactId, String xRegistryVersion, InputStream data) {
		return null;
	}

	@Override
	public ArtifactSearchResults listArtifactsInGroup(String groupId, Integer limit, Integer offset, SortOrder order, SortBy orderby) {

		try {
			final List<NameValuePair> params = List.of(new BasicNameValuePair(Parameters.LIMIT, String.valueOf(limit)),
					new BasicNameValuePair(Parameters.OFFSET, String.valueOf(offset)),
					new BasicNameValuePair(Parameters.SORT_ORDER, order.value()),
					new BasicNameValuePair(Parameters.ORDER_BY, orderby.value()));

			final HttpRequest req = HttpRequest.newBuilder()
					.uri(buildURI(endpoint + Routes.GROUP_BASE_PATH, params, groupId))
					.GET()
					.build();

			final HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());

			if (200 == res.statusCode()) {
				return mapper.readValue(res.body(), ArtifactSearchResults.class);
			}
		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
		return null;
	}

	private RestClientException parseError(Exception ex) {

		//TODO build error
		return new RestClientException(new Error());
	}

	@Override
	public ArtifactMetaData createArtifact(String groupId, ArtifactType xRegistryArtifactType, String xRegistryArtifactId, String xRegistryVersion, IfExists ifExists, Boolean canonical, InputStream data) {

		try {

			final HttpRequest req = HttpRequest.newBuilder()
					.header(Headers.ARTIFACT_ID, xRegistryArtifactId)
					.header(Headers.ARTIFACT_TYPE, xRegistryArtifactType.value())
					.header(Headers.VERSION, xRegistryVersion)
					.uri(buildURI(endpoint + Routes.GROUP_BASE_PATH, Collections.emptyList(), groupId))
					.POST(HttpRequest.BodyPublishers.ofByteArray(data.readAllBytes()))
					.build();

			final HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());

			if (200 == res.statusCode()) {
				return mapper.readValue(res.body(), ArtifactMetaData.class);
			}
		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
		return null;
	}

	@Override
	public void deleteArtifactsInGroup(String groupId) {

	}

	@Override
	public InputStream getContentById(int contentId) {
		return null;
	}

	@Override
	public InputStream getContentByGlobalId(int globalId) {
		return null;
	}

	@Override
	public InputStream getContentByHash(int contentHash, Boolean canonical) {
		return null;
	}

	@Override
	public ArtifactSearchResults searchArtifacts(String name, Integer offset, Integer limit, SortOrder order, SortBy orderby, List<String> labels, List<String> properties, String description, String artifactgroup) {
		return null;
	}

	@Override
	public ArtifactSearchResults searchArtifactsByContent(Integer offset, Integer limit, SortOrder order, SortBy orderby, InputStream data) {
		return null;
	}

	private static URI buildURI(String basePath, List<NameValuePair> query, Object... pathParams) throws URISyntaxException {

		final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, pathParams));

		uriBuilder.setParameters(query);

		return uriBuilder.build();
	}
}
