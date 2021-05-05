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

import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.rest.client.request.ErrorHandler;
import io.apicurio.registry.rest.client.request.Request;
import io.apicurio.registry.rest.client.response.ResponseHandler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.keycloak.authorization.client.util.HttpResponseException;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class VertxRestClient implements RegistryHttpClient {

    private final WebClient webClient;
    private final Map<String, Object> options;
    private final Auth auth;
    private final String basePath;

    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>();
    private static final ThreadLocal<Map<String, String>> requestHeaders = ThreadLocal.withInitial(Collections::emptyMap);

    public VertxRestClient(String basePath, Map<String, Object> options, Auth auth) {
        this.webClient = WebClient.create(Vertx.vertx());
        this.options = options;
        this.auth = auth;
        this.basePath = basePath;
    }

    @Override
    public <T> T sendRequest(Request<T> request) {
        try {
            final URI uri = buildURI(basePath + request.getRequestPath(), request.getQueryParams(), request.getPathParams());
            final RequestOptions requestOptions = new RequestOptions();

            requestOptions.setURI(uri.toString());

            DEFAULT_HEADERS.forEach(requestOptions::addHeader);

            //Add current request headers
            requestHeaders.get().forEach(requestOptions::addHeader);
            requestHeaders.remove();

            Map<String, String> headers = request.getHeaders();
            if (this.auth != null) {
                //make headers mutable...
                headers = new HashMap<>(headers);
                this.auth.apply(headers);
            }
            headers.forEach(requestOptions::addHeader);

            final CompletableFuture<T> resultHolder = new CompletableFuture<T>();
            final ResponseHandler<T> responseHandler = new ResponseHandler<>(resultHolder);

            HttpRequest<Buffer> httpClientRequest;

            switch (request.getOperation()) {
                case GET:
                    httpClientRequest = webClient.request(HttpMethod.GET, requestOptions);
                    break;
                case PUT:
                    httpClientRequest = webClient.request(HttpMethod.PUT, requestOptions.getURI());
                    break;
                case POST:
                    httpClientRequest = webClient.request(HttpMethod.POST, requestOptions.getURI());
                    break;
                case DELETE:
                    httpClientRequest = webClient.request(HttpMethod.DELETE, requestOptions.getURI());
                    break;
                default:
                    throw new IllegalStateException("Operation not allowed");
            }

            httpClientRequest.send(responseHandler);

            return resultHolder.get();

        } catch (URISyntaxException | InterruptedException | HttpResponseException | ExecutionException e) {
            throw ErrorHandler.parseError(e);
        }
    }

    private static URI buildURI(String basePath, Map<String, List<String>> queryParams, List<String> pathParams) throws URISyntaxException {
        Object[] encodedPathParams = pathParams
                .stream()
                .map(VertxRestClient::encodeURIComponent)
                .toArray();
        final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, encodedPathParams));
        final List<NameValuePair> queryParamsExpanded = new ArrayList<>();
        //Iterate over query params list so we can add multiple query params with the same key
        queryParams.forEach((key, paramList) -> paramList
                .forEach(value -> queryParamsExpanded.add(new BasicNameValuePair(key, value))));
        uriBuilder.setParameters(queryParamsExpanded);
        return uriBuilder.build();
    }

    private static String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void setNextRequestHeaders(Map<String, String> headers) {
        requestHeaders.set(headers);
    }

    @Override
    public Map<String, String> getHeaders() {
        return requestHeaders.get();
    }
}
