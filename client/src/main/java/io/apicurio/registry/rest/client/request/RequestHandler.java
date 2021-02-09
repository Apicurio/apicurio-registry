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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RequestHandler {

	private final HttpClient client;
	private final String endpoint;

	public RequestHandler(String endpoint ) {
		if (!endpoint.endsWith("/")) {
			endpoint += "/";
		}
		final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();

		this.endpoint = endpoint;
		this.client = httpClientBuilder.build();
	}

	public  <T> T sendRequest(Operation operation, String requestPath, Map<String, List<String>> queryParams, HttpResponse.BodyHandler<T> bodyHandler, Object... pathParams) {

		return sendRequest(operation, requestPath, Collections.emptyMap(), queryParams, bodyHandler, Optional.empty(), pathParams);
	}

	public <T> T sendRequest(Operation operation, String requestPath, Map<String, String> headers,  Map<String, List<String>> queryParams, HttpResponse.BodyHandler<T> bodyHandler, Optional<InputStream> data, Object... pathParams) {

		try {
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(buildURI(endpoint + requestPath, queryParams, pathParams))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json");

			headers.forEach(requestBuilder::header);

			switch (operation) {
			case GET:
				requestBuilder.GET();
				break;
			case PUT:
				requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(data.get().readAllBytes()));
				break;
			case POST:
				requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(data.get().readAllBytes()));
				break;
			case DELETE:
				requestBuilder.DELETE();
				break;
			default:
				throw new IllegalStateException("Operation not allowed");
		}

		return client.send(requestBuilder.build(), bodyHandler)
				.body();

		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw parseError(e);
		}
	}

	private static URI buildURI(String basePath, Map<String, List<String>> queryParams, Object... pathParams) throws URISyntaxException {

		final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, pathParams));

		final List<NameValuePair> queryParamsExpanded = new ArrayList<>();

		//Iterate over query params list so we can add multiple query params with the same key
		queryParams.forEach((key, paramList) -> paramList
				.forEach(value -> queryParamsExpanded.add(new BasicNameValuePair(key, value))));

		uriBuilder.setParameters(queryParamsExpanded);

		return uriBuilder.build();
	}

	private RestClientException parseError(Exception ex) {

		//TODO build error
		return new RestClientException(new Error());
	}


	public enum Operation {
		PUT, POST, GET, DELETE
	}
}