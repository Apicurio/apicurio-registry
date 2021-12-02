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

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.datamodel.TenantResource;

/**
 * @author Fabian Martinez
 */
@Entity
@Table(name = "tenantlimits")
public class RegistryTenantResourceLimitDto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resourcetype")
    private ResourceType type;

    @Column(name = "resourcelimit")
    private Long limit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenantId")
    private RegistryTenantDto tenant;

    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the type
     */
    public ResourceType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(ResourceType type) {
        this.type = type;
    }

    /**
     * @return the limit
     */
    public Long getLimit() {
        return limit;
    }

    /**
     * @param limit the limit to set
     */
    public void setLimit(Long limit) {
        this.limit = limit;
    }

    public TenantResource toDatamodel() {
        TenantResource r = new TenantResource();
        r.setLimit(this.limit);
        r.setType(this.type);
        return r;
    }

    /**
     * @return the tenant
     */
    public RegistryTenantDto getTenant() {
        return tenant;
    }

    /**
     * @param tenant the tenant to set
     */
    public void setTenant(RegistryTenantDto tenant) {
        this.tenant = tenant;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RegistryTenantResourceLimitDto other = (RegistryTenantResourceLimitDto) obj;
        return Objects.equals(id, other.id);
    }



}
