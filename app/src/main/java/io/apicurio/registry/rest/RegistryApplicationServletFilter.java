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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.mt.MultitenancyProperties;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.mt.TenantIdResolver;
import io.apicurio.registry.services.DisabledApisMatcherService;
import io.apicurio.registry.services.http.ErrorHttpResponse;
import io.apicurio.registry.services.http.RegistryExceptionMapperService;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import org.slf4j.Logger;

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
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Optional;

/**
 * This Servlet Filter combines various functionalities that can be configured using config properties:
 * <p>
 * Multitenancy: the registry can accept per-tenant URLs, accepting requests like /t/{tenantId}/...rest of the api...
 * <p>
 * Disable APIs: it's possible to provide a list of regular expresions to disable API paths.
 * The list of regular expressions will be applied to all incoming requests, if any of them match the request will get a 404 response.
 * Note: this is implemented in a servlet to be able to disable the web UI (/ui), because the web is served with Servlets
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class RegistryApplicationServletFilter implements Filter {

    private ObjectMapper mapper;

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

    @Inject
    RegistryExceptionMapperService exceptionMapper;

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        StringBuilder rewriteContext = new StringBuilder();
        HttpServletRequest req = (HttpServletRequest) request;
        String requestURI = req.getRequestURI();

        if (requestURI != null) {
            Optional<String> tenantIdOpt;
            try {

                tenantIdOpt = tenantIdResolver.resolveTenantId(
                        // Request URI
                        requestURI,
                        // Function to get an HTTP request header value
                        req::getHeader,
                        // Function to get the serverName from the HTTP request
                        req::getServerName,
                        // Handler/callback to do some URL rewriting only if needed
                        (tenantId) -> {
                            String actualUri = requestURI.substring(tenantIdResolver.tenantPrefixLength(tenantId));
                            if (actualUri.length() == 0) {
                                actualUri = "/";
                            }

                            log.debug("tenantId[{}] Rewriting request {} to {}", tenantId, requestURI, actualUri);

                            rewriteContext.append(actualUri);
                        });
            } catch (Throwable throwable) {
                mapException(response, throwable);
                //important to return, to stop the filters chain
                return;
            }


            boolean rewriteRequest = tenantIdOpt.isPresent() && rewriteContext.length() != 0;
            String evaluatedURI = requestURI;
            if (rewriteRequest) {
                evaluatedURI = rewriteContext.toString();
            }

            try {
                var tenantStatus = tenantContext.getTenantStatus();
                if (mtProperties.isMultitenancyEnabled() && tenantStatus != TenantStatusValue.READY) {
                    log.debug("Request {} is rejected because the tenant is not ready. Status is {}",
                            requestURI, tenantStatus.value());
                    HttpServletResponse httpResponse = (HttpServletResponse) response;
                    httpResponse.reset();
                    httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    //important to return, to stop the filters chain
                    return;
                }
            } catch (Throwable throwable) {
                mapException(response, throwable);
                return;
            }

            boolean disabled = disabledApisMatcherService.isDisabled(evaluatedURI);

            if (disabled) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.reset();
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                //important to return, to stop the filters chain
                return;
            } else if (rewriteRequest) {
                RequestDispatcher dispatcher = req.getRequestDispatcher(rewriteContext.toString());
                dispatcher.forward(req, response);
                //important to return, to stop the filters chain
                return;
            }

        }

        chain.doFilter(request, response);
    }

    private void mapException(ServletResponse response, Throwable throwable) throws IOException {
        ErrorHttpResponse res = exceptionMapper.mapException(throwable);
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.reset();
        httpResponse.setStatus(res.getStatus());
        httpResponse.setContentType(MediaType.APPLICATION_JSON);

        getMapper().writeValue(httpResponse.getOutputStream(), res.getError());
    }

    private synchronized ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
        }
        return mapper;
    }

}
