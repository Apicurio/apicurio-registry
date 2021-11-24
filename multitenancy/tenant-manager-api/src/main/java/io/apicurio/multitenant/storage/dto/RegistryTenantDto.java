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
package io.apicurio.multitenant.storage.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.TenantStatusValue;

/**
 * @author Fabian Martinez
 */
@Entity
@Table(name = "tenants")
public class RegistryTenantDto {

    @Id
    @Column(name = "tenantId")
    private String tenantId;

    @Column(name = "createdOn")
    private Date createdOn;

    @Column(name = "modifiedOn")
    private Date modifiedOn;

    @Column(name = "createdBy")
    private String createdBy;

    @Column(name = "organizationId")
    private String organizationId;

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "status")
    private String status;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RegistryTenantResourceLimitDto> resources = new ArrayList<>();

    public RegistryTenantDto() {
        // empty
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    public Date getModifiedOn() {
        return modifiedOn;
    }

    public void setModifiedOn(Date modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && name.length() > 512) {
            name = name.substring(0, 512);
        }
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (description != null && description.length() > 2048) {
            description = description.substring(0, 2048);
        }
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<RegistryTenantResourceLimitDto> getResources() {
        return resources;
    }

    public void setResources(List<RegistryTenantResourceLimitDto> resources) {
        this.resources = resources;
    }

    public void addResource(RegistryTenantResourceLimitDto resource) {
        resources.add(resource);
        resource.setTenant(this);
    }

    public void removeResource(RegistryTenantResourceLimitDto resource) {
        resources.remove(resource);
        resource.setTenant(null);
    }

    public RegistryTenant toDatamodel() {
        final RegistryTenant t = new RegistryTenant();
        t.setTenantId(this.tenantId);
        t.setCreatedOn(this.createdOn);
        t.setCreatedBy(this.createdBy);
        t.setOrganizationId(this.organizationId);
        t.setName(this.getName());
        t.setDescription(this.getDescription());
        t.setResources(
                Optional.ofNullable(this.resources)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .map(RegistryTenantResourceLimitDto::toDatamodel)
                    .collect(Collectors.toList()));
        t.setStatus(TenantStatusValue.fromValue(this.getStatus()));
        return t;
    }

}
