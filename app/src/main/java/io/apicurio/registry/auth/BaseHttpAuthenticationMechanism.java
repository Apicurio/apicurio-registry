/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.auth;

import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Base class for HTTP authentication mechanisms that provides common functionality
 * for handling public paths.
 *
 * Public paths are URL patterns that should be accessible without authentication.
 * These are configured via the apicurio.authn.public-paths property as a
 * comma-separated list of path patterns (e.g., "/health/*,/metrics/*").
 *
 * Path patterns support wildcards:
 * - "/exact/path" - Matches exactly this path
 * - "/path/*" - Matches /path/ and anything under it (single level)
 * - "/path/**" - Matches /path/ and anything under it (multiple levels)
 *
 * Subclasses should call {@link #isPublicPath(RoutingContext)} at the beginning
 * of their authenticate() method and return null if the path is public.
 */
public abstract class BaseHttpAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final Logger log = LoggerFactory.getLogger(BaseHttpAuthenticationMechanism.class);

    @Inject
    protected AuthConfig authConfig;

    private volatile List<PathMatcher> publicPathMatchers;

    /**
     * Checks if the request path is configured as a public path that does not
     * require authentication.
     *
     * @param context the routing context
     * @return true if the path is public and authentication should be skipped
     */
    protected boolean isPublicPath(RoutingContext context) {
        if (publicPathMatchers == null) {
            initializePublicPathMatchers();
        }

        String requestPath = context.request().path();

        for (PathMatcher matcher : publicPathMatchers) {
            if (matcher.matches(requestPath)) {
                log.debug("Request path {} matched public path pattern {}, skipping authentication",
                          requestPath, matcher.pattern);
                return true;
            }
        }

        return false;
    }

    /**
     * Initializes the list of public path matchers from configuration.
     */
    private synchronized void initializePublicPathMatchers() {
        if (publicPathMatchers != null) {
            return; // Already initialized
        }

        if (authConfig.publicPaths.isEmpty() || authConfig.publicPaths.get().trim().isEmpty()) {
            publicPathMatchers = Collections.emptyList();
            return;
        }

        String[] patterns = authConfig.publicPaths.get().split(",");
        publicPathMatchers = Arrays.stream(patterns)
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .map(PathMatcher::new)
                .toList();

        log.info("Initialized {} public path patterns: {}",
                 publicPathMatchers.size(), authConfig.publicPaths.get());
    }

    /**
     * Internal class to match URL paths against patterns with wildcard support.
     */
    private static class PathMatcher {
        private final String pattern;
        private final Pattern regex;

        PathMatcher(String pattern) {
            this.pattern = pattern;
            // Convert glob-style pattern to regex
            String regexPattern = pattern
                    .replace(".", "\\.")           // Escape dots
                    .replace("/**", "/.*")         // ** matches any number of path segments
                    .replace("/*", "/[^/]*")       // * matches single path segment
                    .replace("?", ".");            // ? matches single character

            // Ensure exact match
            if (!regexPattern.endsWith(".*")) {
                regexPattern = regexPattern + "$";
            }
            if (!regexPattern.startsWith("^")) {
                regexPattern = "^" + regexPattern;
            }

            this.regex = Pattern.compile(regexPattern);
        }

        boolean matches(String path) {
            return regex.matcher(path).matches();
        }
    }
}
