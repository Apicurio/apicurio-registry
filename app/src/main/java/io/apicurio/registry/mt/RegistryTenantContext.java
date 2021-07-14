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

import io.apicurio.registry.mt.limits.TenantLimitsConfiguration;

/**
 * Simple POJO class to hold the tenant specific configuration
 *
 * @author Fabian Martinez
 */
public class RegistryTenantContext {

    private final String tenantId;
    private final String tenantOwner;
    private final TenantLimitsConfiguration limitsConfiguration;

    public RegistryTenantContext(String tenantId, String tenantOwner, TenantLimitsConfiguration limitsConfiguration) {
        this.tenantId = tenantId;
        this.tenantOwner = tenantOwner;
        this.limitsConfiguration = limitsConfiguration;
    }

    /**
     * @return the tenantId
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * @return the limitsConfiguration
     */
    public TenantLimitsConfiguration getLimitsConfiguration() {
        return limitsConfiguration;
    }

    /**
     * @return the tenantOwner
     */
    public String getTenantOwner() {
        return tenantOwner;
    }

}
