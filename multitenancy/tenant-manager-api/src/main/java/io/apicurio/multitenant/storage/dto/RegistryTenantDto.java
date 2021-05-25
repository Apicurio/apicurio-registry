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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;

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

    @Column(name = "createdBy")
    private String createdBy;

    @Column(name = "organizationId")
    private String organizationId;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RegistryTenantResourceLimitDto> resources;

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

    public List<RegistryTenantResourceLimitDto> getResources() {
        return resources;
    }

    public void setResources(List<RegistryTenantResourceLimitDto> resources) {
        this.resources = resources;
    }

    public RegistryTenant toDatamodel() {
        final RegistryTenant t = new RegistryTenant();
        t.setTenantId(this.tenantId);
        t.setCreatedOn(this.createdOn);
        t.setCreatedBy(this.createdBy);
        t.setOrganizationId(this.organizationId);
        t.setResources(
                Optional.ofNullable(this.resources)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .map(RegistryTenantResourceLimitDto::toDatamodel)
                    .collect(Collectors.toList()));
        return t;
    }

}
