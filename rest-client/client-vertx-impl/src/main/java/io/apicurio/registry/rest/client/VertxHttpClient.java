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
import io.apicurio.registry.rest.client.config.ClientConfig;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.client.impl.ErrorHandler;
import io.apicurio.registry.rest.client.request.Request;
import io.apicurio.registry.rest.client.response.ResponseHandler;
import io.apicurio.registry.rest.client.spi.RegistryHttpClient;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.IoUtil;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
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
import java.util.stream.Collectors;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class VertxHttpClient implements RegistryHttpClient {

    private final WebClient webClient;
    private final Auth auth;
    private final String basePath;

    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>();
    private static final ThreadLocal<Map<String, String>> requestHeaders = ThreadLocal.withInitial(Collections::emptyMap);

    public VertxHttpClient(Vertx vertx, String basePath, Map<String, Object> options, Auth auth) {
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }
        this.webClient = WebClient.create(vertx);
        this.auth = auth;
        this.basePath = basePath;
        addHeaders(options);
    }

    private static void addHeaders(Map<String, Object> configs) {

        Map<String, String> requestHeaders = configs.entrySet().stream()
                .filter(map -> map.getKey().startsWith(ClientConfig.REGISTRY_REQUEST_HEADERS_PREFIX))
                .collect(Collectors.toMap(map -> map.getKey()
                        .replace(ClientConfig.REGISTRY_REQUEST_HEADERS_PREFIX, ""), map -> map.getValue().toString()));

        if (!requestHeaders.isEmpty()) {
            requestHeaders.forEach(DEFAULT_HEADERS::put);
        }
    }

    @Override
    public <T> T sendRequest(Request<T> request) {
        if (Context.isOnEventLoopThread()) {
            throw new UnsupportedOperationException("Must not be called on event loop");
        }

        try {
            final URI uri = buildURI(basePath + request.getRequestPath(), request.getPathParams());
            final RequestOptions requestOptions = new RequestOptions();

            requestOptions.setHost(uri.getHost());
            requestOptions.setURI(uri.getPath());
            requestOptions.setPort(uri.getPort());

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

            CompletableFuture<T> resultHolder;

            switch (request.getOperation()) {
                case GET:
                    resultHolder = executeGet(request, requestOptions);
                    break;
                case PUT:
                    resultHolder = executePut(request, requestOptions);
                    break;
                case POST:
                    resultHolder = executePost(request, requestOptions);
                    break;
                case DELETE:
                    resultHolder = executeDelete(request, requestOptions);
                    break;
                default:
                    throw new IllegalStateException("Operation not allowed");
            }

            return ConcurrentUtil.result(resultHolder);

        } catch (URISyntaxException | HttpResponseException e) {
            if (e.getCause() != null && e.getCause() instanceof RestClientException) {
                throw (RestClientException) e.getCause();
            } else {
                throw ErrorHandler.parseError(e);
            }
        }
    }

    private <T> CompletableFuture<T> executeGet(Request<T> request, RequestOptions requestOptions) {
        return sendRequestWithoutPayload(HttpMethod.GET, request, requestOptions);
    }

    private <T> CompletableFuture<T> executeDelete(Request<T> request, RequestOptions requestOptions) {
        return sendRequestWithoutPayload(HttpMethod.DELETE, request, requestOptions);
    }

    private <T> CompletableFuture<T> executePost(Request<T> request, RequestOptions requestOptions) {
        return sendRequestWithPayload(HttpMethod.POST, request, requestOptions);
    }

    private <T> CompletableFuture<T> executePut(Request<T> request, RequestOptions requestOptions) {
        return sendRequestWithPayload(HttpMethod.PUT, request, requestOptions);
    }

    private <T> CompletableFuture<T> sendRequestWithoutPayload(HttpMethod httpMethod, Request<T> request, RequestOptions requestOptions) {
        final HttpRequest<Buffer> httpClientRequest = webClient.request(httpMethod, requestOptions);

        //Iterate over query params list so we can add multiple query params with the same key
        request.getQueryParams().forEach((key, paramList) -> paramList
                .forEach(value -> httpClientRequest.setQueryParam(key, value)));

        final CompletableFuture<T> resultHolder = new CompletableFuture<T>();
        final ResponseHandler<T> responseHandler = new ResponseHandler<>(resultHolder, request.getResponseType());
        httpClientRequest.send(responseHandler);
        return resultHolder;
    }

    private <T> CompletableFuture<T> sendRequestWithPayload(HttpMethod httpMethod, Request<T> request, RequestOptions requestOptions) {
        final HttpRequest<Buffer> httpClientRequest = webClient.request(httpMethod, requestOptions);
        final CompletableFuture<T> resultHolder = new CompletableFuture<T>();

        //Iterate over query params list so we can add multiple query params with the same key
        request.getQueryParams().forEach((key, paramList) -> paramList
                .forEach(value -> httpClientRequest.setQueryParam(key, value)));

        final ResponseHandler<T> responseHandler = new ResponseHandler<>(resultHolder, request.getResponseType());

        Buffer buffer = Buffer.buffer(IoUtil.toBytes(request.getData()));
        httpClientRequest.sendBuffer(buffer, responseHandler);

        return resultHolder;
    }

    private static URI buildURI(String basePath, List<String> pathParams) throws URISyntaxException {
        Object[] encodedPathParams = pathParams
                .stream()
                .map(VertxHttpClient::encodeURIComponent)
                .toArray();
        final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, encodedPathParams));
        final List<NameValuePair> queryParamsExpanded = new ArrayList<>();
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
