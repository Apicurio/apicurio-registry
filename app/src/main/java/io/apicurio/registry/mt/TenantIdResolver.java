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

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.UriBuilder;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.Headers;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantIdResolver {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final int TENANT_ID_POSITION = 2;

    @Inject
    @ConfigProperty(name = "registry.multitenancy.base.path")
    String nameMultitenancyBasePath;

    String multitenancyBasePath;

    @Inject
    @ConfigProperty(name = "registry.enable.multitenancy")
    boolean multitenancyEnabled;

    @Inject
    TenantContext tenantContext;

    void init(@Observes StartupEvent ev) {
        if (multitenancyEnabled) {
            log.info("Registry running with multitenancy enabled");
        }
        multitenancyBasePath = "/" + nameMultitenancyBasePath + "/";
    }

    public boolean resolveTenantId(RoutingContext ctx) {
        return resolveTenantId(ctx.request().uri(), () -> ctx.request().getHeader(Headers.TENANT_ID), null);
    }

    public void rewriteTenantRequest(ContainerRequestContext requestContext) {
        URI reqUri = requestContext.getUriInfo().getRequestUri();
        String uri = reqUri.getPath();
        resolveTenantId(uri, () -> requestContext.getHeaderString(Headers.TENANT_ID),
                (tenantId) -> {

                    String actualUri = uri.substring(tenantPrefixLength(tenantId));
                    if (actualUri.length() == 0) {
                        actualUri = "/";
                    }

                    log.debug("Rewriting request {} to {} , tenantId {}", uri, actualUri, tenantId);

                    URI newUri = UriBuilder.fromUri(reqUri).replacePath(actualUri).build();

                    requestContext.setRequestUri(newUri);
                });
    }

    private boolean resolveTenantId(String uri, Supplier<String> tenantIdHeaderProvider, Consumer<String> afterSuccessfullUrlResolution) {
        if (multitenancyEnabled) {
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

    private int tenantPrefixLength(String tenantId) {
        return (multitenancyBasePath + tenantId).length();
    }

}
