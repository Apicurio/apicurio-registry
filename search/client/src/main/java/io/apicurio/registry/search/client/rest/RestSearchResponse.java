/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.search.client.rest;

import io.apicurio.registry.search.client.SearchResponse;

import java.net.HttpURLConnection;

/**
 * @author Ales Justin
 */
public class RestSearchResponse implements SearchResponse {
    private final int status;

    public RestSearchResponse(int status) {
        this.status = status;
    }

    static boolean ok(int status) {
        return status >= HttpURLConnection.HTTP_OK && status < HttpURLConnection.HTTP_MULT_CHOICE;
    }

    @Override
    public boolean ok() {
        return ok(status);
    }

    @Override
    public int status() {
        return status;
    }
}
