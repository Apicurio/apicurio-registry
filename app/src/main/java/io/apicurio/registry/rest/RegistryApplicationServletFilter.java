package io.apicurio.registry.rest;

import io.apicurio.registry.services.DisabledApisMatcherService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This Servlet Filter combines various functionalities that can be configured using config properties:
 * Disable APIs: it's possible to provide a list of regular expressions to disable API paths. The list of
 * regular expressions will be applied to all incoming requests, if any of them match the request will get a
 * 404 response. Note: this is implemented in a servlet to be able to disable the web UI (/ui), because the
 * web is served with Servlets
 */
@ApplicationScoped
public class RegistryApplicationServletFilter implements Filter {
    @Inject
    DisabledApisMatcherService disabledApisMatcherService;

    /**
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse,
     *      jakarta.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String requestURI = req.getRequestURI();

        if (requestURI != null) {

            boolean disabled = disabledApisMatcherService.isDisabled(requestURI);

            if (disabled) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                // reset() would clear the security headers the upstream filters set; preserve them.
                resetKeepingHeaders(httpResponse, "Strict-Transport-Security", "X-Content-Type-Options");
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                // important to return, to stop the filters chain
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Resets the response but preserves the named headers, which {@link HttpServletResponse#reset()} would
     * otherwise clear. The caller lists the headers to keep so it stays in control of what survives.
     */
    private static void resetKeepingHeaders(HttpServletResponse response, String... headerNames) {
        Map<String, String> preserved = new LinkedHashMap<>();
        for (String name : headerNames) {
            String value = response.getHeader(name);
            if (value != null) {
                preserved.put(name, value);
            }
        }
        response.reset();
        preserved.forEach(response::setHeader);
    }

}
