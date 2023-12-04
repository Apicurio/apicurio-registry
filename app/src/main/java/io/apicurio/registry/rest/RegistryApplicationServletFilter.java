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
import io.apicurio.registry.services.DisabledApisMatcherService;
import io.apicurio.registry.services.http.ErrorHttpResponse;
import io.apicurio.registry.services.http.RegistryExceptionMapperService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * This Servlet Filter combines various functionalities that can be configured using config properties:
 * Disable APIs: it's possible to provide a list of regular expressions to disable API paths.
 * The list of regular expressions will be applied to all incoming requests, if any of them match the request will get a 404 response.
 * Note: this is implemented in a servlet to be able to disable the web UI (/ui), because the web is served with Servlets
 *
 */
@ApplicationScoped
public class RegistryApplicationServletFilter implements Filter {

    private ObjectMapper mapper;

    @Inject
    Logger log;

    @Inject
    DisabledApisMatcherService disabledApisMatcherService;

    @Inject
    RegistryExceptionMapperService exceptionMapper;

    /**
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse, jakarta.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String requestURI = req.getRequestURI();

        if (requestURI != null) {

            boolean disabled = disabledApisMatcherService.isDisabled(requestURI);

            if (disabled) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.reset();
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
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
