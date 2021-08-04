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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.RegistryTenantList;
import io.apicurio.multitenant.api.datamodel.SortBy;
import io.apicurio.multitenant.api.datamodel.SortOrder;
import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import io.apicurio.multitenant.client.exception.TenantManagerClientErrorHandler;
import io.apicurio.multitenant.client.exception.TenantManagerClientException;
import io.apicurio.rest.client.JdkHttpClient;
import io.apicurio.rest.client.VertxHttpClient;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.request.Request;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.util.IoUtil;
import io.vertx.core.Vertx;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.rest.client.request.Operation.DELETE;
import static io.apicurio.rest.client.request.Operation.GET;
import static io.apicurio.rest.client.request.Operation.POST;
import static io.apicurio.rest.client.request.Operation.PUT;

/**
 * @author Fabian Martinez
 */
public class TenantManagerClientImpl implements TenantManagerClient {

    private static final String TENANTS_API_BASE_PATH = "api/v1/tenants";
    private static final String TENANTS_API_BASE_PATH_TENANT_PARAM = "api/v1/tenants/%s";

    private final ApicurioHttpClient client;
    private final ObjectMapper mapper;

    public TenantManagerClientImpl(String endpoint) {
        this(endpoint, Collections.emptyMap(), null);
    }

    public TenantManagerClientImpl(String baseUrl, Map<String, Object> configs, Auth auth) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        final String endpoint = baseUrl;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.client = new JdkHttpClient(endpoint, configs, auth, new TenantManagerClientErrorHandler());
    }

    public TenantManagerClientImpl(Vertx vertx, String baseUrl, Map<String, Object> configs, Auth auth) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        final String endpoint = baseUrl;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.client = new VertxHttpClient(vertx, endpoint, configs, auth, new AuthErrorHandler());
    }

    @Override
    public List<RegistryTenant> listTenants() {
        return listTenants(null, 0, 50, null, null).getItems();
    }

    @Override
    public RegistryTenantList listTenants(TenantStatusValue status, Integer offset, Integer limit, SortOrder order, SortBy orderby) {

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

        return client.sendRequest(new Request.RequestBuilder<RegistryTenantList>()
                .operation(GET)
                .path(TENANTS_API_BASE_PATH)
                .queryParams(queryParams)
                .responseType(new TypeReference<RegistryTenantList>() {
                })
                .build());
    }

    @Override
    public RegistryTenant createTenant(NewRegistryTenantRequest tenantRequest) {
        try {
            return client.sendRequest(new Request.RequestBuilder<RegistryTenant>()
                    .operation(POST)
                    .path(TENANTS_API_BASE_PATH)
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(tenantRequest)))
                    .responseType(new TypeReference<RegistryTenant>() {
                    })
                    .build());
        } catch (JsonProcessingException e) {
            throw new TenantManagerClientException(e.getMessage());
        }
    }

    @Override
    public void updateTenant(String tenantId, UpdateRegistryTenantRequest updateRequest) {
        try {
            client.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(TENANTS_API_BASE_PATH_TENANT_PARAM)
                    .pathParams(Collections.singletonList(tenantId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(updateRequest)))
                    .responseType(new TypeReference<Void>() {
                    }).build());

        } catch (IOException e) {
            throw new TenantManagerClientException(e.getMessage());
        }
    }

    @Override
    public RegistryTenant getTenant(String tenantId) {
        return client.sendRequest(new Request.RequestBuilder<RegistryTenant>()
                .operation(GET)
                .path(TENANTS_API_BASE_PATH_TENANT_PARAM)
                .pathParams(Collections.singletonList(tenantId))
                .responseType(new TypeReference<RegistryTenant>() {
                }).build());
    }

    @Override
    public void deleteTenant(String tenantId) {
        client.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(TENANTS_API_BASE_PATH_TENANT_PARAM)
                .pathParams(Collections.singletonList(tenantId))
                .responseType(new TypeReference<Void>() {
                }).build());

    }
}
