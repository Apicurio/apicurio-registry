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

package io.apicurio.registry.rest;

import java.io.IOException;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;

import io.apicurio.registry.mt.MultitenancyProperties;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.mt.TenantIdResolver;
import io.apicurio.registry.services.DisabledApisMatcherService;

/**
 *
 * This Servlet Filter combines various functionalities that can be configured using config properties:
 *
 * Multitenancy: the registry can accept per-tenant URLs, accepting requests like /t/{tenantId}/...rest of the api...
 *
 * Disable APIs: it's possible to provide a list of regular expresions for disable API paths.
 * The list of regular expressions will be applied to all incoming requests, if any of them match the request will get a 404 response.
 * Note: this is implemented in a servlet to be able to disable the web UI (/ui), because the web is served with Servlets
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class RegistryApplicationServletFilter implements Filter {

    @Inject
    Logger log;

    @Inject
    MultitenancyProperties mtProperties;

    @Inject
    TenantIdResolver tenantIdResolver;

    @Inject
    TenantContext tenantContext;

    @Inject
    DisabledApisMatcherService disabledApisMatcherService;

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        StringBuilder rewriteContext = new StringBuilder();

        HttpServletRequest req = (HttpServletRequest) request;
        String requestURI = req.getRequestURI();
        if (requestURI != null) {
            //TODO ensure tenant is authenticated at this point, because tenantIdResolver will fetch tenant's configuration from tenant-manager
            boolean tenantResolved = tenantIdResolver.resolveTenantId(requestURI, () -> req.getHeader(Headers.TENANT_ID),
                    (tenantId) -> {

                        String actualUri = requestURI.substring(tenantIdResolver.tenantPrefixLength(tenantId));
                        if (actualUri.length() == 0) {
                            actualUri = "/";
                        }

                        log.debug("tenantId[{}] Rewriting request {} to {}", tenantId, requestURI, actualUri);

                        rewriteContext.append(actualUri);

                    });

            boolean rewriteRequest = tenantResolved && rewriteContext.length() != 0;
            String evaluatedURI = requestURI;
            if (rewriteRequest) {
                evaluatedURI = rewriteContext.toString();
            }

            if (mtProperties.isMultitenancyEnabled()
                    && !tenantResolved
                    && disabledApisMatcherService.isApiRequest(evaluatedURI)) {
                log.warn("Request {} is rejected because no tenant info found, direct access to apis is disabled when multitenancy is enabled", requestURI);
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.reset();
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                //important to return, to stop the filters chain
                tenantContext.clearContext();
                return;
            }

            boolean disabled = disabledApisMatcherService.isDisabled(evaluatedURI);

            if (disabled) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.reset();
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                //important to return, to stop the filters chain
                tenantContext.clearContext();
                return;
            } else if (rewriteRequest) {
                RequestDispatcher dispatcher = req.getRequestDispatcher(rewriteContext.toString());
                dispatcher.forward(req, response);
                //important to return, to stop the filters chain
                log.debug("Cleaning tenant context");
                tenantContext.clearContext();
                return;
            }

        }

        chain.doFilter(request, response);
    }

}
