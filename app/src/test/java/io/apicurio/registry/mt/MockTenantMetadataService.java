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

package io.apicurio.registry.mt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.quarkus.test.Mock;

/**
 * @author Fabian Martinez
 */
@Mock
public class MockTenantMetadataService extends TenantMetadataService {

    private static final Map<String, RegistryTenant> cache = new ConcurrentHashMap<String, RegistryTenant>();


    /**
     * @see io.apicurio.registry.mt.TenantMetadataService#getTenant(java.lang.String)
     */
    @Override
    public RegistryTenant getTenant(String tenantId) throws TenantNotFoundException {
        var tenant = cache.get(tenantId);
        if (tenant == null) {
            throw new TenantNotFoundException("not found " + tenantId);
        }
        return tenant;
    }


    public void createTenant(RegistryTenant tenant) {
        System.out.println("Creating tenant " + tenant.getTenantId());
        cache.put(tenant.getTenantId(), tenant);
    }


}
