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
                httpResponse.reset();
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                // important to return, to stop the filters chain
                return;
            }
        }

        chain.doFilter(request, response);
    }

}
