/*
 * Copyright 2020 Red Hat
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

import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.registry.mt.limits.TenantLimitsConfiguration;

/**
 * @author eric.wittmann@gmail.com
 */
public interface TenantContext {

    String DEFAULT_TENANT_ID = "_";

    /**
     * Get tenant ID.
     */
    String tenantId();

    String tenantOwner();

    default String getTenantIdOrElse(String alternative) {
        return isLoaded() ? tenantId() : alternative;
    }

    TenantLimitsConfiguration limitsConfig();

    void setContext(RegistryTenantContext ctx);

    void clearContext();

    boolean isLoaded();

    TenantStatusValue getTenantStatus();
}
