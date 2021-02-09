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

package io.apicurio.registry.rest.client.request;

import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.v2.beans.Error;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RequestHandler {

	private final HttpClient client;
	private final String endpoint;

	public RequestHandler(String endpoint) {
		if (!endpoint.endsWith("/")) {
			endpoint += "/";
		}
		final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();

		this.endpoint = endpoint;
		this.client = httpClientBuilder.build();
	}

	public <T> T sendGetRequest(String requestPath, Map<String, String> queryParams, HttpResponse.BodyHandler<T> bodyHandler, Object... pathParams) {

		try {
			final HttpRequest req = HttpRequest.newBuilder()
					.uri(buildURI(endpoint + requestPath, queryParams, pathParams))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.GET()
					.build();

			return client.send(req, bodyHandler)
					.body();
		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
	}

	public <T> T sendPostRequest(String requestPath, Map<String, String> headers, Map<String, String> queryParams, HttpResponse.BodyHandler<T> bodyHandler, InputStream data, Object... pathParams) {

		try {
			final HttpRequest.Builder builder = HttpRequest.newBuilder();

			headers.forEach(builder::header);

			builder.uri(buildURI(endpoint + requestPath, queryParams, pathParams))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.POST(HttpRequest.BodyPublishers.ofByteArray(data.readAllBytes()));

			return client.send(builder.build(), bodyHandler)
					.body();

		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
	}

	public <T> T sendPutRequest(String requestPath, Map<String, String> queryParams, HttpResponse.BodyHandler<T> bodyHandler, InputStream data, Object... pathParams) {

		try {
			final HttpRequest req = HttpRequest.newBuilder()
					.uri(buildURI(endpoint + requestPath, queryParams, pathParams))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.PUT(HttpRequest.BodyPublishers.ofByteArray(data.readAllBytes()))
					.build();

			return client.send(req, bodyHandler)
					.body();

		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
	}

	public <T> T sendDeleteRequest(String requestPath, Map<String, String> queryParams, HttpResponse.BodyHandler<T> bodyHandler, Object... pathParams) {

		try {
			final HttpRequest req = HttpRequest.newBuilder()
					.uri(buildURI(endpoint + requestPath, queryParams, pathParams))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.DELETE()
					.build();

			return client.send(req, bodyHandler)
					.body();

		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
	}

	private static URI buildURI(String basePath, Map<String, String> queryParams, Object... pathParams) throws URISyntaxException {

		final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, pathParams));

		uriBuilder.setParameters(queryParams.entrySet()
				.stream()
				.map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList())
		);

		return uriBuilder.build();
	}

	private RestClientException parseError(Exception ex) {

		//TODO build error
		return new RestClientException(new Error());
	}
}