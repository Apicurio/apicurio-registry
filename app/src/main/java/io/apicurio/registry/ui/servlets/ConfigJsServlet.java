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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.ui.beans.ConfigJs;
import io.apicurio.registry.ui.config.UiConfigProperties;
import io.apicurio.registry.utils.StringUtil;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Generates the 'config.js' file imported by the UI.
 * @author eric.wittmann@gmail.com
 */
public class ConfigJsServlet extends HttpServlet {

    private static final long serialVersionUID = 1624928159818173418L;

    @Inject
    UiConfigProperties uiConfig;

    @Inject
    SecurityIdentity identity;

    @Inject
    AuthConfig authConfig;

    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String ct = "application/javascript; charset=" + StandardCharsets.UTF_8;
        response.setContentType(ct);
        JsonFactory f = new JsonFactory();
        try (JsonGenerator g = f.createGenerator(response.getOutputStream(), JsonEncoding.UTF8)) {
            response.getOutputStream().write("var ApicurioRegistryConfig = ".getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
            g.setCodec(mapper);
            g.useDefaultPrettyPrinter();

            ConfigJs config = new ConfigJs();

            config.artifacts.url = this.generateApiUrl(request);

            config.ui.contextPath = uiConfig.getUiContextPath();

            config.features.readOnly = uiConfig.isFeatureReadOnly();
            config.features.settings = uiConfig.isFeatureSettings();
            config.features.breadcrumbs = true;

            configureAuth(config);

            g.writeObject(config);

            g.flush();
            response.getOutputStream().write(";".getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Configure the auth settings.
     * @param config
     */
    private void configureAuth(ConfigJs config) {
        if (uiConfig.isAuthenticationEnabled()) {
            //when auth is enabled but the type is not set, default to keycloak
            if (uiConfig.getUiAuthType().equals("keycloakjs") || uiConfig.getUiAuthType().equals("none")) {
                config.auth.type = "keycloakjs";
                config.auth.options = uiConfig.getKeycloakProperties();
            } else if (uiConfig.getUiAuthType().equals("oidc")) {
                config.auth.type = "oidc";
                config.auth.options = new HashMap<>();
                config.auth.options.put("clientId", uiConfig.getOidcClientId());
                config.auth.options.put("url", uiConfig.getOidcUrl());
                config.auth.options.put("redirectUri", uiConfig.getOidcRedirectUrl());
            }

            config.auth.rbacEnabled = authConfig.isRbacEnabled();
            config.auth.obacEnabled = authConfig.isObacEnabled();
            config.features.roleManagement = authConfig.isApplicationRbacEnabled();
        } else {
            config.auth.type = "none";
        }
    }

    /**
     * Generates a URL that the caller can use to access the API.
     * @param request
     */
    private String generateApiUrl(HttpServletRequest request) {
        String apiRelativePath = "/apis/registry";
        try {
            String apiUrl = uiConfig.getApiUrl();
            if (!"_".equals(apiUrl) && !StringUtil.isEmpty(apiUrl)) {
                return apiUrl;
            }

            String url = resolveUrlFromXForwarded(request, apiRelativePath);
            if (url != null) {
                return url;
            }

            url = request.getRequestURL().toString();
            url = new URI(url).resolve(apiRelativePath).toString();
            if (url.startsWith("http:") && request.isSecure()) {
                url = url.replaceFirst("http", "https");
            }
            return url;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves a URL path relative to the information found in X-Forwarded-Host and X-Forwarded-Proto.
     * @param path
     */
    private String resolveUrlFromXForwarded(HttpServletRequest request, String path) {
        try {
            String fproto = request.getHeader("X-Forwarded-Proto");
            String fhost = request.getHeader("X-Forwarded-Host");
            if (!StringUtil.isEmpty(fproto) && !StringUtil.isEmpty(fhost)) {
                return new URI(fproto + "://" + fhost).resolve(path).toString();
            }
        } catch (URISyntaxException e) {
        }
        return null;
    }

}
