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
 * Adds the X-Content-Type-Options header to all HTTP responses, telling browsers not to MIME-sniff the
 * response body away from its declared content type.
 */
public class ContentTypeOptionsFilter implements Filter {

    private static final String CONTENT_TYPE_OPTIONS_HEADER = "nosniff";

    public static void addContentTypeOptionsHeader(HttpServletResponse httpResponse) {
        httpResponse.setHeader("X-Content-Type-Options", CONTENT_TYPE_OPTIONS_HEADER);
    }

    public ContentTypeOptionsFilter() {
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    // Set before chain.doFilter(), so a downstream response.reset() clears it: the disabled-API 404 path
    // re-lists this header in RegistryApplicationServletFilter#resetKeepingHeaders.
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        addContentTypeOptionsHeader(httpResponse);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
