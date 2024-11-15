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

package io.apicurio.common.apps.web.servlets;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Generates the 'config.js' file imported by the UI.
 * 
 * @author eric.wittmann@gmail.com
 */
@SuppressWarnings("serial")
public abstract class ConfigJsServlet extends HttpServlet {

    private static boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }

    /**
     * @see jakarta.servlet.http.HttpServlet#doGet(jakarta.servlet.http.HttpServletRequest,
     *      jakarta.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String ct = "application/javascript; charset=" + StandardCharsets.UTF_8;
        response.setContentType(ct);
        JsonFactory factory = new JsonFactory();
        try (JsonGenerator generator = factory.createGenerator(response.getOutputStream(),
                JsonEncoding.UTF8)) {
            String varName = getVarName();
            String varDecl = "var " + varName + " = ";
            response.getOutputStream().write(varDecl.getBytes("UTF-8")); //$NON-NLS-1$
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
            generator.setCodec(mapper);
            generator.useDefaultPrettyPrinter();

            Object config = generateConfig(request);

            generator.writeObject(config);

            generator.flush();
            response.getOutputStream().write(";".getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Called to get the variable name for the output JSONP payload.
     * 
     * @return the JSONP variable name
     */
    protected abstract String getVarName();

    /**
     * Called to generate the configuration object. This will then be serialized to JSON as the response
     * payload.
     * 
     * @param request the http request
     * @return generated config
     */
    protected abstract Object generateConfig(HttpServletRequest request);

    /**
     * Generates a URL that the caller can use to access the REST API.
     * 
     * @param request the HTTP request
     * @return the generated API URL
     */
    protected String generateApiUrl(HttpServletRequest request) {
        try {
            String apiUrl = getApiUrlOverride();
            if (apiUrl == null) {
                String apiRelativePath = getApiRelativePath();
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
            } else {
                return apiUrl;
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the API URL override. Returns null if none is configured.
     * 
     * @return the API URL override
     */
    protected abstract String getApiUrlOverride();

    /**
     * Gets the relative path of the API.
     * 
     * @return the relative API path
     */
    protected abstract String getApiRelativePath();

    /**
     * Resolves a URL path relative to the information found in X-Forwarded-Host and X-Forwarded-Proto.
     * 
     * @param request http request
     * @param path relative path
     * @return the resolved URL
     */
    protected static String resolveUrlFromXForwarded(HttpServletRequest request, String path) {
        try {
            String fproto = request.getHeader("X-Forwarded-Proto");
            String fhost = request.getHeader("X-Forwarded-Host");
            if (!isEmpty(fproto) && !isEmpty(fhost)) {
                return new URI(fproto + "://" + fhost).resolve(path).toString();
            }
        } catch (URISyntaxException e) {
        }
        return null;
    }

}
