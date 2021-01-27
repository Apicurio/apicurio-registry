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

import io.apicurio.registry.rest.Headers;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author eric.wittmann@gmail.com
 */
public class TenantRequestFilter {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String MULTITENANT_BASE_PATH = "/t/";

    @ConfigProperty(name = "registry.enable.multitenancy")
    boolean multitenancyEnabled;

    @Inject
    TenantContext tenantContext;

    void init(@Observes StartupEvent ev) {
        if (multitenancyEnabled) {
            log.info("Registry running with multitenancy enabled");
        }
    }

    @RouteFilter(value = RouteFilter.DEFAULT_PRIORITY + 1000)
    public void tenantRedirectFilter(RoutingContext ctx) throws IOException {

        if (multitenancyEnabled) {

            String uri = ctx.request().uri();

            log.debug("Filtering request {} current tenantId {}", uri, tenantContext.tenantId());

            if (uri.startsWith(MULTITENANT_BASE_PATH)) {
                String[] tokens = uri.split("/");
                String tenantId = tokens[2];
                tenantContext.tenantId(tenantId);

                String actualUri = uri.substring(tenantPrefixLength(tenantId));

                log.debug("Rerouting request {} to {} tenantId {}", uri, actualUri, tenantContext.tenantId());

                ctx.reroute(actualUri);
                //very important to return
                return;
            }

            String tenantId = ctx.request().getHeader(Headers.TENANT_ID);
            if (tenantId != null) {
                tenantContext.tenantId(tenantId);
            }

            if (tenantContext.isLoaded()) {
                ctx.response().putHeader(Headers.TENANT_ID, tenantContext.tenantId());
            }

        }

        ctx.next();

    }

    private int tenantPrefixLength(String tenantId) {
        return (MULTITENANT_BASE_PATH + tenantId).length();
    }

}
