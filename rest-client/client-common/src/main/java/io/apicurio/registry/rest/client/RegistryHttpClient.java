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

import io.apicurio.registry.rest.client.request.Request;

import java.util.Map;

/**
 * Common interface for registry http client implementations
 */
public interface RegistryHttpClient {

    /**
     * @param request The request to be executed
     * @param <T> The type of the param to be returned
     * @return The response from the client
     */
    <T> T sendRequest(Request<T> request);

    /**
     * @param headers the request headers to be used in the next request
     */
    void setNextRequestHeaders(Map<String, String> headers);

    /**
     * @return The current map with the request headers
     */
    Map<String, String> getHeaders();
}
