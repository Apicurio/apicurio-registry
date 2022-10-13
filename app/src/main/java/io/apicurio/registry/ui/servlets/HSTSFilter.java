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

package io.apicurio.registry.ui.servlets;

import org.slf4j.Logger;

import java.io.IOException;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Add HSTS headers to all HTTP responses.  Browser will ignore the header if the connection
 * is not secure.
 * @author eric.wittmann@gmail.com
 */
public class HSTSFilter implements Filter {

    private static final long MAX_AGE = 31536000; // one year
    private static final String HSTS_HEADER = "max-age=" + MAX_AGE + "; includeSubdomains";

    public static void addHstsHeaders(HttpServletResponse httpResponse) {
        httpResponse.setHeader("Strict-Transport-Security", HSTS_HEADER);
    }

    @Inject
    Logger log;

    /**
     * C'tor
     */
    public HSTSFilter() {
    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.debug("HSTSFilter is executed on request URI {}", ((HttpServletRequest)request).getRequestURI());
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        addHstsHeaders(httpResponse);
        chain.doFilter(request, response);
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }
}
