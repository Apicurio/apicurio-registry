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

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import io.apicurio.registry.rest.Headers;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.RoutingContext;

/**
 * This class centralizes the logic to resolve the tenantId from an http request.
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantIdResolver {

    private static final int TENANT_ID_POSITION = 2;

    String multitenancyBasePath;

    @Inject
    Logger log;

    @Inject
    MultitenancyProperties mtProperties;

    @Inject
    TenantContext tenantContext;

    @Inject
    TenantContextLoader contextLoader;

    @Inject
    TenantMetadataService tenantMetadataService;

    @Inject
    SecurityIdentity securityIdentity;

    @ConfigProperty(name = "registry.organization-id.claim-name")
    String organizationIdClaimName;

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
            log.trace("Resolving tenantId for request {}", uri);

            if (uri.startsWith(multitenancyBasePath)) {
                String[] tokens = uri.split("/");
                // 0 is empty
                // 1 is t
                // 2 is the tenantId
                String tenantId = tokens[TENANT_ID_POSITION];
                checkTenantAuthorization(tenantId);
                RegistryTenantContext context = contextLoader.loadContext(tenantId);
                tenantContext.setContext(context);
                if (afterSuccessfullUrlResolution != null) {
                    afterSuccessfullUrlResolution.accept(tenantId);
                }
                return true;
            }

            String tenantId = tenantIdHeaderProvider.get();
            if (tenantId != null) {
                RegistryTenantContext context = contextLoader.loadContext(tenantId);
                tenantContext.setContext(context);
                return true;
            }

        }
        //apply default tenant context
        tenantContext.setContext(contextLoader.defaultTenantContext());
        return false;
    }

    public int tenantPrefixLength(String tenantId) {
        return (multitenancyBasePath + tenantId).length();
    }

    private void checkTenantAuthorization(String tenantId) {
        final RegistryTenant tenant = tenantMetadataService.getTenant(tenantId);
        final String accessedOrganizationId = securityIdentity.getAttribute(organizationIdClaimName);

        if (!tenantCanAccessOrganization(tenant, accessedOrganizationId)) {
            throw new TenantNotAuthorizedException(String.format("Tenant %s not authorized to access organization %s", tenantId, accessedOrganizationId));
        }
    }

    private boolean tenantCanAccessOrganization(RegistryTenant tenant, String accessedOrganizationId) {
        return tenant == null || accessedOrganizationId.equals(tenant.getOrganizationId());
    }
}
