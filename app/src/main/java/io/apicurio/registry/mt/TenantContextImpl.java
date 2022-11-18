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

import io.apicurio.registry.mt.limits.TenantLimitsConfiguration;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.MDC;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import java.util.Optional;

/**
 * @author eric.wittmann@gmail.com
 */
@RequestScoped
public class TenantContextImpl implements TenantContext {

    private static final String TENANT_ID_KEY = "tenantId";
    private Optional<RegistryTenantContext> current = Optional.empty();

    private static final RegistryTenantContext EMPTY_CONTEXT = new RegistryTenantContext(DEFAULT_TENANT_ID, null, null, TenantStatusValue.READY, null);
    private static final ThreadLocal<RegistryTenantContext> CURRENT = ThreadLocal.withInitial(() -> EMPTY_CONTEXT);

    @Inject
    TenantContextLoader contextLoader;

    @Inject
    TenantIdResolver tenantIdResolver;

    @Inject
    CurrentVertxRequest request;

    @Inject
    MultitenancyProperties multitenancyProperties;

    @PostConstruct
    public void load() {
        RegistryTenantContext loadedContext;
        if (multitenancyProperties.isMultitenancyEnabled() && request.getCurrent() != null) {
            HttpServerRequest req = request.getCurrent().request();
            String requestURI = req.uri();

            Optional<String> tenantIdOpt = tenantIdResolver.resolveTenantId(
                    // Request URI
                    requestURI,
                    // Function to get an HTTP request header value
                    req::getHeader,
                    // Function to get the serverName from the HTTP request
                    req::host, null);

            loadedContext = tenantIdOpt.map(tenantId -> contextLoader.loadRequestContext(tenantId))
                    .orElse(contextLoader.defaultTenantContext());

        } else {
            loadedContext = contextLoader.defaultTenantContext();
        }

        setContext(loadedContext);
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#tenantId()
     */
    @Override
    public String tenantId() {
        return current.map(RegistryTenantContext::getTenantId)
                .orElse(CURRENT.get().getTenantId());
    }

    /**
     * @see TenantContext#currentContext()
     */
    @Override
    public RegistryTenantContext currentContext() {
        return current.orElse(CURRENT.get());
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#tenantOwner()
     */
    @Override
    public String tenantOwner() {
        return current.map(RegistryTenantContext::getTenantOwner)
                .orElse(CURRENT.get().getTenantOwner());
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#limitsConfig()
     */
    @Override
    public TenantLimitsConfiguration limitsConfig() {
        return current.map(RegistryTenantContext::getLimitsConfiguration)
                .orElse(CURRENT.get().getLimitsConfiguration());
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#setContext(RegistryTenantContext)
     */
    @Override
    public void setContext(RegistryTenantContext ctx) {
        current = Optional.of(ctx);
        MDC.put(TENANT_ID_KEY, ctx.getTenantId());
    }

    /**
     * @see io.apicurio.registry.mt.TenantContext#clearContext()
     */
    @Override
    public void clearContext() {
        current = Optional.of(EMPTY_CONTEXT);
        MDC.remove(TENANT_ID_KEY);
    }

    @Override
    public boolean isLoaded() {
        return !tenantId().equals(DEFAULT_TENANT_ID);
    }

    @Override
    public TenantStatusValue getTenantStatus() {
        return current.map(RegistryTenantContext::getStatus)
                .orElse(CURRENT.get().getStatus());
    }
}
