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

import javax.enterprise.context.RequestScoped;

/**
 * @author eric.wittmann@gmail.com
 */
@RequestScoped
public class TenantContextImpl implements TenantContext {

    protected static final String DEFAULT_TENANT_ID = "_";

    protected String tenantId = DEFAULT_TENANT_ID;

    /**
     * @see io.apicurio.registry.mt.TenantContext#tenantId()
     */
    @Override
    public String tenantId() {
        return this.tenantId;
    }

    /**
     * Sets the tenantId to this context, this operation can only happen once per context instance.
     */
    @Override
    public void tenantId(String tenantId) {
        if (!this.tenantId.equals(DEFAULT_TENANT_ID)) {
            throw new IllegalAccessError("Tenant context can only be initialized once");
        }
        this.tenantId = tenantId;
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#isLoaded()
     */
    @Override
    public boolean isLoaded() {
        return !this.tenantId.equals(DEFAULT_TENANT_ID);
    }

}
