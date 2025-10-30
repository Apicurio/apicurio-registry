package io.apicurio.registry.ui.servlets;

import io.apicurio.registry.ui.ApiDocsConfigProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Filter that processes API documentation responses to inject dynamic content and context path support.
 *
 * This filter performs two main functions:
 * 1. Replaces placeholder tokens in HTML content (SPEC_URL, API_TITLE) with actual values
 * 2. Injects application context path into HTML base tags and CSS resource URLs for proxy support
 *
 * The filter supports deployment scenarios where the application is served behind a reverse proxy
 * with a custom context path by modifying resource references appropriately.
 */
@ApplicationScoped
public class ApiDocsFilter implements Filter {

    @Inject
    ApiDocsConfigProperties config;

    /**
     * @see jakarta.servlet.Filter#init(jakarta.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /**
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse,
     *      jakarta.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        CharResponseWrapper wrappedResponse = new CharResponseWrapper((HttpServletResponse) resp);
        chain.doFilter(req, wrappedResponse);

        byte[] bytes = wrappedResponse.getByteArray();
        if (bytes != null && bytes.length > 0 && resp.getContentType() != null) {
            String contentType = resp.getContentType();

            if (contentType.contains("text/html")) {
                processHtmlContent(req, resp, bytes);
            } else if (contentType.contains("text/css")) {
                processCssContent(resp, bytes);
            } else {
                resp.getOutputStream().write(bytes);
            }
        }
    }

    /**
     * Processes HTML content by:
     * 1. Replacing SPEC_URL and API_TITLE placeholders with actual values
     * 2. Injecting context path into the base tag if configured
     */
    private void processHtmlContent(ServletRequest req, ServletResponse resp, byte[] bytes)
            throws IOException {
        String contextPath = normalizeContextPath(config.contextPath);
        String out = new String(bytes, StandardCharsets.UTF_8);

        // Replace placeholders for OpenAPI spec URL and API title
        String specUrl = generateSpecUrl((HttpServletRequest) req);
        String title = generateSpecTitle((HttpServletRequest) req);
        out = out.replace("SPEC_URL", specUrl);
        out = out.replace("API_TITLE", title);

        // Inject context path into base tag if not using default "/"
        if (!contextPath.equals("/")) {
            String baseTag = String.format("<base href=\"%s\">", contextPath + "/");
            out = out.replace("<base href=\"/\">", baseTag);
        }

        byte[] newBytes = out.getBytes(StandardCharsets.UTF_8);
        resp.setContentLength(newBytes.length);
        resp.getOutputStream().write(newBytes);
    }

    /**
     * Processes CSS content by replacing absolute /resources/ paths with context-path-prefixed paths.
     */
    private void processCssContent(ServletResponse resp, byte[] bytes) throws IOException {
        String contextPath = normalizeContextPath(config.contextPath);
        String out = new String(bytes, StandardCharsets.UTF_8);

        String resourcesPath = contextPath.equals("/") ? "/resources/" : contextPath + "/resources/";
        out = out.replace("url(/resources/", "url(" + resourcesPath);

        byte[] newBytes = out.getBytes(StandardCharsets.UTF_8);
        resp.setContentLength(newBytes.length);
        resp.getOutputStream().write(newBytes);
    }

    /**
     * Generates the URL for the OpenAPI specification based on the servlet path.
     *
     * @param request the HTTP servlet request
     * @return the generated spec URL
     */
    private String generateSpecUrl(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String contextPath = normalizeContextPath(config.contextPath);
        String apiSpecPath = contextPath.equals("/") ? "/api-specifications/" : contextPath + "/api-specifications/";
        String apiSpec = servletPath.replace("/apis/", apiSpecPath);
        if (!apiSpec.endsWith("/")) {
            apiSpec += "/";
        }
        apiSpec += "openapi.json";

        return apiSpec;
    }

    /**
     * Generates a human-readable title for the API based on the servlet path.
     *
     * @param request the HTTP servlet request
     * @return the API title
     */
    private String generateSpecTitle(HttpServletRequest request) {
        String servletPath = request.getServletPath();

        if (servletPath.contains("registry/v3")) {
            return "Core Registry API (v3)";
        }
        if (servletPath.contains("registry/v2")) {
            return "Core Registry API (v2)";
        }
        if (servletPath.contains("ccompat")) {
            return "Confluent Schema Registry API";
        }

        return "";
    }

    /**
     * Normalizes the context path to ensure it has a leading slash and no trailing slash
     * (unless it's just "/").
     *
     * @param path the context path to normalize
     * @return the normalized context path
     */
    private String normalizeContextPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "/";
        }

        // Ensure leading slash
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // Remove trailing slash
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
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
         * @param baos the byte array output stream
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