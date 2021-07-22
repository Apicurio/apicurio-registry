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
package io.apicurio.multitenant.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.multitenant.client.exception.TenantManagerClientException;
import io.apicurio.multitenant.client.exception.RegistryTenantNotFoundException;
import io.apicurio.multitenant.api.beans.RegistryTenantList;
import io.apicurio.multitenant.api.beans.SortBy;
import io.apicurio.multitenant.api.beans.SortOrder;
import io.apicurio.multitenant.api.beans.TenantStatusValue;
import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;


import io.apicurio.rest.client.auth.KeycloakAuth;

/**
 * @author Fabian Martinez
 */
public class TenantManagerClientImpl implements TenantManagerClient {

    private static final String TENANTS_API_BASE_PATH = "api/v1/tenants";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final KeycloakAuth auth;

    public TenantManagerClientImpl(String endpoint) {
        this(endpoint, null);

    }

    public TenantManagerClientImpl(String endpoint, KeycloakAuth auth) {
        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        this.endpoint = endpoint;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.auth = auth;
    }

    @Override
    public List<RegistryTenant> listTenants() {
        return listTenants(null, 0, 50, null, null).getItems();
    }

    @Override
    public RegistryTenantList listTenants(TenantStatusValue status, Integer offset, Integer limit, SortOrder order, SortBy orderby) {
        try {
            final Map<String, String> headers = prepareRequestHeaders();

            Map<String, List<String>> queryParams = new HashMap<>();
            if (status != null) {
                queryParams.put("status", Arrays.asList(status.value()));
            }
            if (offset != null) {
                queryParams.put("offset", Arrays.asList(String.valueOf(offset)));
            }
            if (limit != null) {
                queryParams.put("limit", Arrays.asList(String.valueOf(limit)));
            }
            if (order != null) {
                queryParams.put("order", Arrays.asList(order.value()));
            }
            if (orderby != null) {
                queryParams.put("orderby", Arrays.asList(orderby.value()));
            }

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(buildURI(endpoint + TENANTS_API_BASE_PATH, queryParams, Collections.emptyList()))
                    .GET();

            headers.forEach(req::header);

            HttpResponse<InputStream> res = client.send(req.build(), BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                return this.mapper.readValue(res.body(), new TypeReference<RegistryTenantList>() {
                });
            }
            throw new TenantManagerClientException(res.toString());
        } catch (IOException | InterruptedException e) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public RegistryTenant createTenant(NewRegistryTenantRequest tenantRequest) {
        try {
            final Map<String, String> headers = prepareRequestHeaders();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + TENANTS_API_BASE_PATH))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofByteArray(this.mapper.writeValueAsBytes(tenantRequest)));

            headers.forEach(req::header);
            HttpResponse<InputStream> res = client.send(req.build(), BodyHandlers.ofInputStream());
            if (res.statusCode() == 201) {
                return this.mapper.readValue(res.body(), RegistryTenant.class);
            }
            throw new TenantManagerClientException(res.toString());
        } catch (IOException | InterruptedException e) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public void updateTenant(String tenantId, UpdateRegistryTenantRequest updateRequest) {
        try {
            final Map<String, String> headers = prepareRequestHeaders();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + TENANTS_API_BASE_PATH + "/" + tenantId))
                    .header("Content-Type", "application/json")
                    .PUT(BodyPublishers.ofByteArray(this.mapper.writeValueAsBytes(updateRequest)));

            headers.forEach(req::header);
            HttpResponse<InputStream> res = client.send(req.build(), BodyHandlers.ofInputStream());
            if (res.statusCode() == 204) {
                return;
            }
            throw new TenantManagerClientException(res.toString());
        } catch (IOException | InterruptedException e) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public RegistryTenant getTenant(String tenantId) {
        try {
            final Map<String, String> headers = prepareRequestHeaders();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + TENANTS_API_BASE_PATH + "/" + tenantId))
                    .GET();
            headers.forEach(req::header);

            HttpResponse<InputStream> res = client.send(req.build(), BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                return this.mapper.readValue(res.body(), RegistryTenant.class);
            } else if (res.statusCode() == 404) {
                throw new RegistryTenantNotFoundException(res.toString());
            }
            throw new TenantManagerClientException(res.toString());
        } catch (IOException | InterruptedException e) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public void deleteTenant(String tenantId) {
        try {
            final Map<String, String> headers = prepareRequestHeaders();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + TENANTS_API_BASE_PATH + "/" + tenantId))
                    .DELETE();
            headers.forEach(req::header);

            HttpResponse<InputStream> res = client.send(req.build(), BodyHandlers.ofInputStream());
            if (res.statusCode() != 204) {
                throw new TenantManagerClientException(res.toString());
            }
        } catch (IOException | InterruptedException e) {
            throw new TenantManagerClientException(e);
        }
    }

    private Map<String, String> prepareRequestHeaders() {
        final Map<String, String> headers = new HashMap<>();
        if (null != auth) {
            auth.apply(headers);
        }
        return headers;
    }

    private URI buildURI(String basePath, Map<String, List<String>> queryParams, List<String> pathParams) throws URISyntaxException {
        Object[] encodedPathParams = pathParams
                .stream()
                .map(this::encodeURIComponent)
                .toArray();
        final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, encodedPathParams));
        final List<NameValuePair> queryParamsExpanded = new ArrayList<>();
        //Iterate over query params list so we can add multiple query params with the same key
        queryParams.forEach((key, paramList) -> paramList
                .forEach(value -> queryParamsExpanded.add(new BasicNameValuePair(key, value))));
        uriBuilder.setParameters(queryParamsExpanded);
        return uriBuilder.build();
    }

    private String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
