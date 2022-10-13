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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;


/**
 * Note: simple filtering of response content - found on Stack Overflow here:
 *
 *   https://stackoverflow.com/a/14741213
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class SpecUrlFilter implements Filter {

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        CharResponseWrapper wrappedResponse = new CharResponseWrapper((HttpServletResponse) resp);
        chain.doFilter(req, wrappedResponse);

        byte[] bytes = wrappedResponse.getByteArray();
        if (bytes != null && resp.getContentType() != null && resp.getContentType().contains("text/html")) {
            String specUrl = this.generateSpecUrl((HttpServletRequest) req);
            String title = this.generateSpecTitle((HttpServletRequest) req);

            String out = new String(bytes, StandardCharsets.UTF_8);
            out = out.replace("SPEC_URL", specUrl);
            out = out.replace("API_TITLE", title);
            byte[] newBytes = out.getBytes(StandardCharsets.UTF_8);
            resp.setContentLength(newBytes.length);
            resp.getOutputStream().write(newBytes);
        } else if (bytes != null && bytes.length > 0) {
            resp.getOutputStream().write(bytes);
        }
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }

    private static class ByteArrayServletStream extends ServletOutputStream {
        ByteArrayOutputStream baos;

        /**
         * Constructor.
         * @param baos
         */
        ByteArrayServletStream(ByteArrayOutputStream baos) {
            this.baos = baos;
        }

        /**
         * @see java.io.OutputStream#write(int)
         */
        @Override
        public void write(int param) throws IOException {
            baos.write(param);
        }

        /**
         * @see javax.servlet.ServletOutputStream#isReady()
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * @see javax.servlet.ServletOutputStream#setWriteListener(javax.servlet.WriteListener)
         */
        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }

    private static class ByteArrayPrintWriter {

        private ByteArrayOutputStream baos = new ByteArrayOutputStream();

        private PrintWriter pw = new PrintWriter(baos);

        private ServletOutputStream sos = new ByteArrayServletStream(baos);

        public PrintWriter getWriter() {
            return pw;
        }

        public ServletOutputStream getStream() {
            return sos;
        }

        byte[] toByteArray() {
            return baos.toByteArray();
        }
    }

    public class CharResponseWrapper extends HttpServletResponseWrapper {
        private ByteArrayPrintWriter output;
        private boolean usingWriter;

        public CharResponseWrapper(HttpServletResponse response) {
            super(response);
            usingWriter = false;
            output = new ByteArrayPrintWriter();
        }

        public byte[] getByteArray() {
            return output.toByteArray();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            // will error out, if in use
            if (usingWriter) {
                super.getOutputStream();
            }
            usingWriter = true;
            return output.getStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            // will error out, if in use
            if (usingWriter) {
                super.getWriter();
            }
            usingWriter = true;
            return output.getWriter();
        }

        @Override
        public String toString() {
            return output.toString();
        }
    }

    /**
     * Generates a URL that the caller can use to access the API.
     * @param request
     */
    private String generateSpecUrl(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String apiSpec = servletPath.replace("/apis/", "/api-specifications/");
        if (!apiSpec.endsWith("/")) {
            apiSpec += "/";
        }
        apiSpec += "openapi.json";

        return apiSpec;
    }

    private String generateSpecTitle(HttpServletRequest request) {
        String servletPath = request.getServletPath();

        if (servletPath.contains("registry/v1")) {
            return "Core Registry API (v1)";
        }
        if (servletPath.contains("registry/v2")) {
            return "Core Registry API (v2)";
        }
        if (servletPath.contains("ccompat")) {
            return "Confluent Schema Registry API";
        }
        if (servletPath.contains("ibmcompat")) {
            return "IBM Schema Registry API";
        }
        if (servletPath.contains("cncf")) {
            return "CNCF Schema Registry API";
        }

        return "";
    }
}
