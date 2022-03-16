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

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.RegistryTenantList;
import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.datamodel.SortBy;
import io.apicurio.multitenant.api.datamodel.SortOrder;
import io.apicurio.multitenant.api.datamodel.TenantResource;
import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenant;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenantList;
import io.apicurio.tenantmanager.api.datamodel.NewApicurioTenantRequest;
import io.apicurio.tenantmanager.api.datamodel.UpdateApicurioTenantRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Fabian Martinez
 */
public class TenantManagerClientImpl implements TenantManagerClient {

    private final io.apicurio.tenantmanager.client.TenantManagerClient tenantManagerClient;

    public TenantManagerClientImpl(String endpoint) {
        this(endpoint, Collections.emptyMap(), null);
    }

    public TenantManagerClientImpl(String baseUrl, Map<String, Object> configs, Auth auth) {
        this.tenantManagerClient = new io.apicurio.tenantmanager.client.TenantManagerClientImpl(baseUrl, configs, auth);
    }

    @Override
    public List<RegistryTenant> listTenants() {
        var tenantList = tenantManagerClient.listTenants(null, 0, 50, null, null);

        var registryTenantList = convertTenantListToRegistryTenantList(tenantList);

        return registryTenantList.getItems();
    }

    @Override
    public RegistryTenantList listTenants(TenantStatusValue status, Integer offset, Integer limit, SortOrder order, SortBy orderby) {
        io.apicurio.tenantmanager.api.datamodel.TenantStatusValue st = io.apicurio.tenantmanager.api.datamodel.TenantStatusValue.valueOf(status.value());
        io.apicurio.tenantmanager.api.datamodel.SortOrder ord = io.apicurio.tenantmanager.api.datamodel.SortOrder.valueOf(status.value());
        io.apicurio.tenantmanager.api.datamodel.SortBy sb = io.apicurio.tenantmanager.api.datamodel.SortBy.valueOf(orderby.value());

        var tenantList = tenantManagerClient.listTenants(st, offset, limit, ord, sb);

        return convertTenantListToRegistryTenantList(tenantList);
    }

    @Override
    public RegistryTenant createTenant(NewRegistryTenantRequest tenantRequest) {
        NewApicurioTenantRequest newApicurioTenantRequest = new NewApicurioTenantRequest();
        newApicurioTenantRequest.setTenantId(tenantRequest.getTenantId());
        newApicurioTenantRequest.setOrganizationId(tenantRequest.getOrganizationId());
        newApicurioTenantRequest.setName(tenantRequest.getName());
        newApicurioTenantRequest.setDescription(tenantRequest.getDescription());
        newApicurioTenantRequest.setCreatedBy(tenantRequest.getCreatedBy());
        newApicurioTenantRequest.setResources(registryTenantResourceToApicurioTenantResource(tenantRequest.getResources()));

        ApicurioTenant createdTenant = tenantManagerClient.createTenant(newApicurioTenantRequest);

        return convertApicurioTenantToRegistryTenant(createdTenant);
    }

    @Override
    public void updateTenant(String tenantId, UpdateRegistryTenantRequest updateRequest) {
        UpdateApicurioTenantRequest newApicurioTenantRequest = new UpdateApicurioTenantRequest();
        newApicurioTenantRequest.setName(updateRequest.getName());
        newApicurioTenantRequest.setDescription(updateRequest.getDescription());
        newApicurioTenantRequest.setResources(registryTenantResourceToApicurioTenantResource(updateRequest.getResources()));

        tenantManagerClient.updateTenant(tenantId, newApicurioTenantRequest);
    }

    @Override
    public RegistryTenant getTenant(String tenantId) {
       var tenant = tenantManagerClient.getTenant(tenantId);

       return convertApicurioTenantToRegistryTenant(tenant);
    }

    @Override
    public void deleteTenant(String tenantId) {
       tenantManagerClient.deleteTenant(tenantId);
    }

    private RegistryTenantList convertTenantListToRegistryTenantList(ApicurioTenantList tenants) {
        var registryTenantList = new RegistryTenantList();
        List<RegistryTenant> registryTenants = new ArrayList<>();
        for (ApicurioTenant tenant: tenants.getItems()) {
            var registryTenant = convertApicurioTenantToRegistryTenant(tenant);
            registryTenants.add(registryTenant);
        }

        registryTenantList.setItems(registryTenants);
        return registryTenantList;
    }

    private RegistryTenant convertApicurioTenantToRegistryTenant(ApicurioTenant tenant) {
        var registryTenant = new RegistryTenant();

        registryTenant.setTenantId(tenant.getTenantId());
        registryTenant.setCreatedOn(tenant.getCreatedOn());
        registryTenant.setCreatedBy(tenant.getCreatedBy());
        registryTenant.setDescription(tenant.getDescription());
        registryTenant.setOrganizationId(tenant.getOrganizationId());

        var resources = new ArrayList<TenantResource>();
        for (var tenantResource : tenant.getResources()) {
            var type = ResourceType.valueOf(tenantResource.getType());
            var newTenantResource = new TenantResource(type, tenantResource.getLimit());
            resources.add(newTenantResource);
        }
        registryTenant.setResources(resources);
        TenantStatusValue.fromValue(tenant.getStatus().value());
        registryTenant.setStatus(TenantStatusValue.fromValue(tenant.getStatus().value()));

        return registryTenant;
    }

    private List<io.apicurio.tenantmanager.api.datamodel.TenantResource> registryTenantResourceToApicurioTenantResource(List<TenantResource> tenantResources) {
        var resources = new ArrayList<io.apicurio.tenantmanager.api.datamodel.TenantResource>();
        for (var tenantResource : tenantResources) {
            var type = ResourceType.valueOf(tenantResource.getType().value());
            var newTenantResource = new io.apicurio.tenantmanager.api.datamodel.TenantResource();
            newTenantResource.setType(type.value());
            newTenantResource.setLimit(tenantResource.getLimit());
            resources.add(newTenantResource);
        }
        return resources;
    }
}
