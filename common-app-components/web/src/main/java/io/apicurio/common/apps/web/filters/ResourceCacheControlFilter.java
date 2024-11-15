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
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link Filter} to add cache control headers for resources such as CSS and images.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class ResourceCacheControlFilter implements Filter {

    public static void disableHttpCaching(HttpServletResponse httpResponse) {
        Date now = new Date();
        httpResponse.setDateHeader("Date", now.getTime()); //$NON-NLS-1$
        httpResponse.setDateHeader("Expires", expiredSinceYesterday(now)); //$NON-NLS-1$
        httpResponse.setHeader("Pragma", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
        httpResponse.setHeader("Cache-control", "no-cache, no-store, must-revalidate"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static long expiredSinceYesterday(Date now) {
        return now.getTime() - 86400000L;
    }

    private List<Pattern> disabledForUrls;

    /**
     * C'tor
     */
    public ResourceCacheControlFilter() {
    }

    /**
     * @see jakarta.servlet.Filter#init(jakarta.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        String disabledForConfig = config.getInitParameter("disabledFor");
        disabledForUrls = new LinkedList<>();
        if (disabledForConfig != null) {
            String[] urls = disabledForConfig.split(",");
            for (String url : urls) {
                if (!url.isBlank()) {
                    disabledForUrls.add(Pattern.compile(url));
                }
            }
        }
    }

    /**
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse,
     *      jakarta.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String requestURI = ((HttpServletRequest) request).getRequestURI();
        Date now = new Date();
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setDateHeader("Date", now.getTime()); //$NON-NLS-1$

        boolean disableCaching = false;
        if (requestURI == null) {
            disableCaching = true;
        }

        for (Pattern pattern : disabledForUrls) {
            if (pattern.matcher(requestURI).matches()) {
                disableCaching = true;
            }
        }

        if (disableCaching) {
            disableHttpCaching(httpResponse);
        } else {
            // Cache most files for one year
            httpResponse.setDateHeader("Expires", expiresInOneYear(now)); //$NON-NLS-1$
            httpResponse.setHeader("Cache-control", "public, max-age=31536000"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        chain.doFilter(request, response);
    }

    private long expiresInOneYear(Date now) {
        return now.getTime() + 31536000000L;
    }

    /**
     * @see jakarta.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }
}
