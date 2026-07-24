package io.apicurio.registry.ui.servlets;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Adds security headers to all HTTP responses.
 * Browser will ignore the HSTS header if the connection is not secure.
 * X-Content-Type-Options is intentionally co-located here to centralize
 * response hardening in a single filter.
 */
public class HSTSFilter implements Filter {

    private static final long MAX_AGE = 31536000; // one year
    private static final String HSTS_HEADER = "max-age=" + MAX_AGE + "; includeSubDomains";
    private static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
    private static final String X_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";

    public static void addHstsHeaders(HttpServletResponse httpResponse) {
        httpResponse.setHeader("Strict-Transport-Security", HSTS_HEADER);
        httpResponse.setHeader(X_CONTENT_TYPE_OPTIONS_HEADER, X_CONTENT_TYPE_OPTIONS_VALUE);
    }

    /**
     * Resets the response but preserves the HSTS and X-Content-Type-Options headers, which
     * {@link HttpServletResponse#reset()} would otherwise clear. Callers that reset the response to
     * produce an error should use this instead.
     */
    public static void resetKeepingHstsHeader(HttpServletResponse httpResponse) {
        String hsts = httpResponse.getHeader("Strict-Transport-Security");
        String xcto = httpResponse.getHeader(X_CONTENT_TYPE_OPTIONS_HEADER);
        httpResponse.reset();
        if (hsts != null) {
            httpResponse.setHeader("Strict-Transport-Security", hsts);
        }
        if (xcto != null) {
            httpResponse.setHeader(X_CONTENT_TYPE_OPTIONS_HEADER, xcto);
        }
    }

    /**
     * C'tor
     */
    public HSTSFilter() {
    }

    /**
     * @see jakarta.servlet.Filter#init(jakarta.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    /**
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse,
     *      jakarta.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        addHstsHeaders(httpResponse);
        chain.doFilter(request, response);
    }

    /**
     * @see jakarta.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }
}
