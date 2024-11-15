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

package io.apicurio.common.apps.web.filters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Note: simple filtering of response content - found on Stack Overflow here:
 * https://stackoverflow.com/a/14741213
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class BaseHrefFilter implements Filter {

    private static final String BASE_HREF_PATTERN = "<base href=\"HREF\">";

    private String from;
    private String to;

    /**
     * @see jakarta.servlet.Filter#init(jakarta.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String fromHref = filterConfig.getInitParameter("fromHref");
        String toHref = filterConfig.getInitParameter("toHref");
        if (fromHref == null) {
            throw new ServletException("Missing init param: 'fromHref'");
        }
        if (toHref == null) {
            throw new ServletException("Missing init param: 'toHref'");
        }
        from = BASE_HREF_PATTERN.replace("HREF", fromHref);
        to = BASE_HREF_PATTERN.replace("HREF", toHref);
    }

    /**
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse,
     *      jakarta.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        CharResponseWrapper wrappedResponse = new CharResponseWrapper((HttpServletResponse) response);
        chain.doFilter(request, wrappedResponse);

        byte[] bytes = wrappedResponse.getByteArray();
        if (bytes != null && response.getContentType() != null
                && response.getContentType().contains("text/html")) {
            String out = new String(bytes, StandardCharsets.UTF_8);
            out = out.replace(from, to);
            byte[] newBytes = out.getBytes(StandardCharsets.UTF_8);
            response.setContentLength(newBytes.length);
            response.getOutputStream().write(newBytes);
        } else if (bytes != null && bytes.length > 0) {
            response.getOutputStream().write(bytes);
        }
    }

    /**
     * @see jakarta.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }

    private static class ByteArrayServletStream extends ServletOutputStream {
        ByteArrayOutputStream baos;

        /**
         * Constructor.
         * 
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
         * @see jakarta.servlet.ServletOutputStream#isReady()
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * @see jakarta.servlet.ServletOutputStream#setWriteListener(jakarta.servlet.WriteListener)
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

}
