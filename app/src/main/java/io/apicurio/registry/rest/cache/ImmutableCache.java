package io.apicurio.registry.rest.cache;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Interceptor binding annotation for adding HTTP caching headers to immutable content endpoints.
 *
 * Methods annotated with this will have HTTP caching headers added to their Response objects:
 * - Cache-Control: public, immutable, max-age=31536000 (1 year)
 * - ETag: "{id}" (extracted from method parameters)
 * - Vary: Accept, Accept-Encoding
 *
 * This annotation is processed by {@link ImmutableCacheInterceptor}.
 */
@InterceptorBinding
@Retention(RUNTIME)
@Target({ METHOD, TYPE })
public @interface ImmutableCache {
}
