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

import javax.enterprise.context.ApplicationScoped;
import org.slf4j.MDC;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class TenantContextImpl implements TenantContext {

    private static final String TENANT_ID_KEY = "tenantId";
    private static final String DEFAULT_TENANT_ID = "_";

    private static final ThreadLocal<RegistryTenantContext> CURRENT = ThreadLocal.withInitial(() -> new RegistryTenantContext(DEFAULT_TENANT_ID));


    public static RegistryTenantContext current() {
        return CURRENT.get();
    }

    public static void setCurrentContext(RegistryTenantContext context) {
        CURRENT.set(context);
    }

    public static void clearCurrentContext() {
        CURRENT.remove();
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#tenantId()
     */
    @Override
    public String tenantId() {
        return CURRENT.get().getTenantId();
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#tenantId(java.lang.String)
     */
    @Override
    public void tenantId(String tenantId) {
        CURRENT.set(new RegistryTenantContext(tenantId));
        MDC.put(TENANT_ID_KEY, tenantId);
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#clearTenantId()
     */
    @Override
    public void clearTenantId() {
        this.tenantId(DEFAULT_TENANT_ID);
        MDC.remove(TENANT_ID_KEY);
    }

    @Override
    public boolean isLoaded() {
        return !tenantId().equals(DEFAULT_TENANT_ID);
    }

}
