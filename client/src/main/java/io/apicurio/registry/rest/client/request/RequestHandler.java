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

import io.apicurio.registry.auth.Auth;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.keycloak.authorization.client.util.HttpResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RequestHandler {

    private final HttpClient client;
    private final String endpoint;
    private final Auth auth;
    private static final Map<String, String> DEFAULT_HEADERS = Map.of("Content-Type", "application/json", "Accept", "application/json");
    private static final ThreadLocal<Map<String, String>> requestHeaders = ThreadLocal.withInitial(Collections::emptyMap);

    public RequestHandler(String endpoint, Auth auth) {
        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }

        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        this.endpoint = endpoint;
        this.auth = auth;
        this.client = httpClientBuilder.build();
    }

    public <T> T sendRequest(Request<T> request) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(buildURI(endpoint + request.getRequestPath(), request.getQueryParams(), request.getPathParams()));

            DEFAULT_HEADERS.forEach(requestBuilder::header);

            //Add current request headers
            requestHeaders.get().forEach(requestBuilder::header);
            requestHeaders.remove();

            Map<String, String> headers = request.getHeaders();
            if (auth != null) {
                //make headers mutable...
                headers = new HashMap<>(headers);
                auth.apply(headers);
            }
            headers.forEach(requestBuilder::header);

            switch (request.getOperation()) {
                case GET:
                    requestBuilder.GET();
                    break;
                case PUT:
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getData().readAllBytes()));
                    break;
                case POST:
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(request.getData().readAllBytes()));
                    break;
                case DELETE:
                    requestBuilder.DELETE();
                    break;
                default:
                    throw new IllegalStateException("Operation not allowed");
            }

            return client.send(requestBuilder.build(), new BodyHandler<>(request.getResponseClass()))
                    .body()
                    .get();

        } catch (URISyntaxException | IOException | InterruptedException | HttpResponseException e) {
            throw ErrorHandler.parseError(e);
        }
    }

    private static URI buildURI(String basePath, Map<String, List<String>> queryParams, List<String> pathParams) throws URISyntaxException {
        final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, pathParams.toArray()));
        final List<NameValuePair> queryParamsExpanded = new ArrayList<>();
        //Iterate over query params list so we can add multiple query params with the same key
        queryParams.forEach((key, paramList) -> paramList
                .forEach(value -> queryParamsExpanded.add(new BasicNameValuePair(key, value))));
        uriBuilder.setParameters(queryParamsExpanded);
        return uriBuilder.build();
    }

    public void setNextRequestHeaders(Map<String, String> headers) {
        requestHeaders.set(headers);
    }

    public Map<String, String> getHeaders() {
        return requestHeaders.get();
    }
}