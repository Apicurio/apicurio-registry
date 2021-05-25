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
package io.apicurio.multitenant.api;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.dto.DtoMappers;
import io.apicurio.multitenant.storage.RegistryTenantStorage;
import io.apicurio.multitenant.storage.TenantNotFoundException;
import io.apicurio.multitenant.storage.dto.RegistryTenantDto;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantsResourceImpl implements TenantsResource {

    @Inject
    RegistryTenantStorage tenantsRepository;

    @Override
    public List<RegistryTenant> getTenants() {
        return tenantsRepository.listAll()
                .stream()
                .map(RegistryTenantDto::toDatamodel)
                .collect(Collectors.toList());
    }

    @Override
    public Response createTenant(NewRegistryTenantRequest tenantRequest) {

        required(tenantRequest.getTenantId(), "TenantId is mandatory");
        required(tenantRequest.getOrganizationId(), "OrganizationId is mandatory");

        RegistryTenantDto tenant = new RegistryTenantDto();

        tenant.setTenantId(tenantRequest.getTenantId());

        tenant.setOrganizationId(tenantRequest.getOrganizationId());

        tenant.setCreatedOn(new Date());
        tenant.setCreatedBy(null); //TODO extract user from auth details

        if (tenantRequest.getResources() != null) {
            //find duplicates, invalid config
            Set<ResourceType> items = new HashSet<>();
            for (var resource : tenantRequest.getResources()) {
                if (!items.add(resource.getType())) {
                    throw new BadRequestException(
                            String.format("Invalid configuration, resource type %s is duplicated", resource.getType().name()));
                }
            }

            tenant.setResources(tenantRequest.getResources()
                    .stream()
                    .map(DtoMappers::toStorageDto)
                    .collect(Collectors.toList()));
        }

        tenantsRepository.save(tenant);

        return Response.status(Status.CREATED).entity(tenant.toDatamodel()).build();
    }

    @Override
    public RegistryTenant getTenant(@PathParam("tenantId") String tenantId) {
        return tenantsRepository.findByTenantId(tenantId)
                .map(RegistryTenantDto::toDatamodel)
                .orElseThrow(() -> TenantNotFoundException.create(tenantId));
    }

    @Override
    public Response deleteTenant(@PathParam("tenantId") String tenantId) {

        tenantsRepository.delete(tenantId);

        return Response.noContent().build();
    }

    private void required(String parameter, String message) {
        if (parameter == null || parameter.isEmpty()) {
            throw new BadRequestException(message);
        }
    }

}
