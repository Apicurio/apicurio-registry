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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.multitenant.client.exception.TenantManagerClientException;
import io.apicurio.multitenant.client.exception.RegistryTenantNotFoundException;
import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import org.keycloak.common.VerificationException;

/**
 * @author Fabian Martinez
 */
public class TenantManagerClientImpl implements TenantManagerClient {

    private static final String TENANTS_API_BASE_PATH = "api/v1/tenants";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final Auth auth;

    public TenantManagerClientImpl(String endpoint) {
        this(endpoint, null);

    }

    public TenantManagerClientImpl(String endpoint, Auth auth) {
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
        try {
            final Map<String, String> headers = prepareRequestHeaders();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + TENANTS_API_BASE_PATH))
                    .GET();

            headers.forEach(req::header);

            HttpResponse<InputStream> res = client.send(req.build(), BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                return this.mapper.readValue(res.body(), new TypeReference<List<RegistryTenant>>() {
                });
            }
            throw new TenantManagerClientException(res.toString());
        } catch (IOException | InterruptedException | VerificationException e) {
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
        } catch (IOException | InterruptedException | VerificationException e) {
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
        } catch (IOException | InterruptedException | VerificationException e) {
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
        } catch (IOException | InterruptedException | VerificationException e) {
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
        } catch (IOException | InterruptedException | VerificationException e) {
            throw new TenantManagerClientException(e);
        }
    }

    private Map<String, String> prepareRequestHeaders() throws VerificationException {
        final Map<String, String> headers = new HashMap<>();
        if (null != auth) {
            headers.put("Authorization", auth.obtainAuthorizationValue());
        }
        return headers;
    }
}
