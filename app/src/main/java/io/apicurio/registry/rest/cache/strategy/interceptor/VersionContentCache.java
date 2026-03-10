package io.apicurio.registry.rest.cache.strategy.interceptor;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for HTTP caching using VersionContentCacheStrategy.
 * The method must be annotated with @MethodMetadata that extracts required version expression parameter.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface VersionContentCache {

    /**
     * Name of the extracted parameter that contains String version expression.
     * If specified, the interceptor will include this in the cache strategy.
     */
    @Nonbinding
    String versionExpressionParam() default "";

    /**
     * Name of the extracted parameter that contains HandleReferencesType.
     * If specified, the interceptor will include this in the cache strategy.
     */
    @Nonbinding
    String referencesParam() default "";

    /**
     * Name of the extracted parameter that contains ReferenceType.
     * If specified, the interceptor will include this in the cache strategy.
     */
    @Nonbinding
    String refTypeParam() default "";
}
