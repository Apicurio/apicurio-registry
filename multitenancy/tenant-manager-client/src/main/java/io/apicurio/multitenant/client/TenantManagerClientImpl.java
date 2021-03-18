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
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.multitenant.client.exception.TenantManagerClientException;
import io.apicurio.multitenant.client.exception.RegistryTenantNotFoundException;
import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;

/**
 * @author Fabian Martinez
 */
public class TenantManagerClientImpl implements TenantManagerClient {

    private static final String TENANTS_API_BASE_PATH = "api/v1/tenants";

    private HttpClient client;
    private ObjectMapper mapper;

    private String endpoint;

    public TenantManagerClientImpl(String endpoint) {
        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        this.endpoint = endpoint;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public List<RegistryTenant> listTenants() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + TENANTS_API_BASE_PATH))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                return this.mapper.readValue(res.body(), new TypeReference<List<RegistryTenant>>(){});
            }
            throw new TenantManagerClientException(res.toString());
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public RegistryTenant createTenant(NewRegistryTenantRequest tenantRequest) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + TENANTS_API_BASE_PATH))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofByteArray(this.mapper.writeValueAsBytes(tenantRequest)))
                    .build();

            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() == 201) {
                return this.mapper.readValue(res.body(), RegistryTenant.class);
            }
            throw new TenantManagerClientException(res.toString());
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public RegistryTenant getTenant(String tenantId) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + TENANTS_API_BASE_PATH + "/" + tenantId))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                return this.mapper.readValue(res.body(), RegistryTenant.class);
            } else if (res.statusCode() == 404) {
                throw new RegistryTenantNotFoundException(res.toString());
            }
            throw new TenantManagerClientException(res.toString());
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public void deleteTenant(String tenantId) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + TENANTS_API_BASE_PATH + "/" + tenantId))
                .DELETE()
                .build();
        try {
            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() != 204) {
                throw new TenantManagerClientException(res.toString());
            }
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

}
