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
 * Add HSTS headers to all HTTP responses. Browser will ignore the header if the connection is not secure.
 */
public class HSTSFilter implements Filter {

    private static final long MAX_AGE = 31536000; // one year
    private static final String HSTS_HEADER = "max-age=" + MAX_AGE + "; includeSubdomains";

    public static void addHstsHeaders(HttpServletResponse httpResponse) {
        httpResponse.setHeader("Strict-Transport-Security", HSTS_HEADER);
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
