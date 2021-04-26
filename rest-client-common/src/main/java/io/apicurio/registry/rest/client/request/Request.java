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

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.rest.client.request.provider.Operation;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class Request<T> {

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String ACCEPT = "Accept";

    private final Operation operation;
    private final String requestPath;
    private final Map<String, String> headers;
    private final Map<String, List<String>> queryParams;
    private final TypeReference<T> responseType;
    private final InputStream data;
    private final List<String> pathParams;

    private Request(Operation operation, String requestPath, Map<String, String> headers, Map<String, List<String>> queryParams, TypeReference<T> responseType, InputStream data, List<String> pathParams) {
        this.operation = operation;
        this.requestPath = requestPath;
        this.headers = new HashMap<>(headers);
        this.queryParams = queryParams;
        this.responseType = responseType;
        this.data = data;
        this.pathParams = pathParams;

        if (!this.headers.containsKey(CONTENT_TYPE)) {
            this.headers.put(CONTENT_TYPE, "application/json");
        }
        if (!this.headers.containsKey(ACCEPT)) {
            this.headers.put(ACCEPT, "application/json");
        }
    }

    public Operation getOperation() {
        return operation;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public TypeReference<T> getResponseType() {
        return responseType;
    }

    public InputStream getData() {
        return data;
    }

    public List<String> getPathParams() {
        return pathParams;
    }

    public static class RequestBuilder<T> {
        private Operation operation;
        private String path;
        private Map<String, String> headers = Collections.emptyMap();
        private Map<String, List<String>> queryParams = Collections.emptyMap();
        private TypeReference<T> typeReference;
        private InputStream data;
        private List<String> pathParams = Collections.emptyList();

        public RequestBuilder<T> operation(Operation operation) {
            this.operation = operation;
            return this;
        }

        public RequestBuilder<T> path(String requestPath) {
            this.path = requestPath;
            return this;
        }

        public RequestBuilder<T> headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public RequestBuilder<T> queryParams(Map<String, List<String>> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public RequestBuilder<T> responseType(TypeReference<T> typeReference) {
            this.typeReference = typeReference;
            return this;
        }

        public RequestBuilder<T> data(InputStream data) {
            this.data = data;
            return this;
        }

        public RequestBuilder<T> pathParams(List<String> pathParams) {
            this.pathParams = pathParams;
            return this;
        }

        public Request<T> build() {
            return new Request<>(operation, path, headers, queryParams, typeReference, data, pathParams);
        }
    }
}
