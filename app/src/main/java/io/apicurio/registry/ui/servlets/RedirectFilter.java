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

package io.apicurio.registry.ui.servlets;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.common.apps.config.Info;


/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class RedirectFilter implements Filter {
    
    @ConfigProperty(name = "registry.enable-redirects")
    @Info(category = "redirects", description = "Enable redirects", availableSince = "2.1.2.Final")
    Boolean redirectsEnabled;

    @ConfigProperty(name = "registry.redirects")
    @Info(category = "redirects", description = "Registry redirects", availableSince = "2.1.2.Final")
    Map<String, String> redirectsConfig;
    Map<String, String> redirects = new HashMap<>();

    @PostConstruct
    void init() {
        if (redirectsEnabled != null && redirectsEnabled) {
            redirectsConfig.values().forEach(value -> {
                String [] split = value.split(",");
                if (split != null && split.length == 2) {
                    String key = split[0];
                    String val = split[1];
                    redirects.put(key, val);
                }
            });
        }
    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (redirectsEnabled != null && redirectsEnabled) {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;
    
            String servletPath = request.getServletPath();
    
            if (servletPath == null || "".equals(servletPath)) {
                servletPath = "/";
            }
    
            if (redirects.containsKey(servletPath)) {
                response.sendRedirect(redirects.get(servletPath));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }

}
