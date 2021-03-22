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

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.RegistryApplicationServletFilter;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.RoutingContext;

/**
 * This class centralizes the logic to resolve the tenantId from an http request.
 *
 * In deployments with authentication enabled the {@link io.apicurio.registry.services.tenant.CustomTenantConfigResolver} will
 * get triggered first and it will attempt to resolve the tenantId before {@link RegistryApplicationServletFilter}, but
 * the TenantRequestFilter will attempt to resolve the tenantId anyway.
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantIdResolver {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final int TENANT_ID_POSITION = 2;

    String multitenancyBasePath;

    @Inject
    MultitenancyProperties mtProperties;

    @Inject
    TenantContext tenantContext;

    void init(@Observes StartupEvent ev) {
        if (mtProperties.isMultitenancyEnabled()) {
            log.info("Registry running with multitenancy enabled");
        }
        multitenancyBasePath = "/" + mtProperties.getNameMultitenancyBasePath() + "/";
    }

    public boolean resolveTenantId(RoutingContext ctx) {
        return resolveTenantId(ctx.request().uri(), () -> ctx.request().getHeader(Headers.TENANT_ID), null);
    }

    public boolean resolveTenantId(String uri, Supplier<String> tenantIdHeaderProvider, Consumer<String> afterSuccessfullUrlResolution) {
        if (mtProperties.isMultitenancyEnabled()) {
            log.debug("Resolving tenantId for request {}", uri);

            if (uri.startsWith(multitenancyBasePath)) {
                String[] tokens = uri.split("/");
                // 0 is empty
                // 1 is t
                // 2 is the tenantId
                String tenantId = tokens[TENANT_ID_POSITION];
                tenantContext.tenantId(tenantId);
                if (afterSuccessfullUrlResolution != null) {
                    afterSuccessfullUrlResolution.accept(tenantId);
                }
                return true;
            }

            String tenantId = tenantIdHeaderProvider.get();
            if (tenantId != null) {
                tenantContext.tenantId(tenantId);
                return true;
            }

        }
        tenantContext.clearTenantId();
        return false;
    }

    public int tenantPrefixLength(String tenantId) {
        return (multitenancyBasePath + tenantId).length();
    }

}
