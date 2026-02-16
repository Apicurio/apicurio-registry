package io.apicurio.registry.util;

import jakarta.interceptor.Interceptor;

/**
 * Centralized constants for framework priorities.
 */
public final class Priorities {

    public static final class Interceptors {

        /**
         * Priority for {@link io.apicurio.registry.rest.MethodMetadataInterceptor}.
         */
        public static final int METHOD_METADATA = Interceptor.Priority.APPLICATION - 300; // 1700

        /**
         * Priority for {@link io.apicurio.registry.logging.audit.AuditedInterceptor}.
         */
        public static final int AUDIT = Interceptor.Priority.APPLICATION - 200; // 1800

        /**
         * Priority for {@link io.apicurio.registry.auth.AuthorizedInterceptor}.
         */
        public static final int AUTHORIZATION = Interceptor.Priority.APPLICATION - 100; // 1900

        /**
         * Priority for core application interceptors that don't have ordering dependencies.
         */
        public static final int APPLICATION = Interceptor.Priority.APPLICATION; // 2000

        /**
         * Priority for HTTP caching interceptors.
         */
        public static final int CACHE = Interceptor.Priority.APPLICATION + 100; // 2100

        private Interceptors() {
        }
    }

    public static final class RequestResponseFilters {

        /**
         * Priority for {@link io.apicurio.registry.logging.audit.HttpRequestsAuditFilter}.
         */
        public static final int AUDIT = jakarta.ws.rs.Priorities.AUTHENTICATION - 100; // 900

        /**
         * Priority for core application filters that don't have ordering dependencies.
         */
        public static final int APPLICATION = jakarta.ws.rs.Priorities.USER; // 5000

        /**
         * Priority for HTTP caching filters.
         */
        public static final int CACHE = jakarta.ws.rs.Priorities.USER + 100; // 5100

        private RequestResponseFilters() {
        }
    }

    private Priorities() {
    }
}
