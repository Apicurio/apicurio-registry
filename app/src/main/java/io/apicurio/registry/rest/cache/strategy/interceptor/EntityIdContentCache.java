package io.apicurio.registry.rest.cache.strategy.interceptor;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for HTTP caching using EntityIdContentCacheStrategy.
 * The method must be annotated with @MethodMetadata that extracts an MPK_ENTITY_ID parameter.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface EntityIdContentCache {

    /**
     * Name of the extracted parameter that contains HandleReferencesType.
     * If specified, the interceptor will include this in the cache strategy.
     */
    @Nonbinding
    String referencesParam() default "";

    /**
     * Name of the extracted parameter that contains Boolean returnArtifactType.
     * If specified, the interceptor will include this in the cache strategy.
     */
    @Nonbinding
    String returnArtifactTypeParam() default "";

    /**
     * Name of the extracted parameter that contains ReferenceType.
     * If specified, the interceptor will include this in the cache strategy.
     */
    @Nonbinding
    String refTypeParam() default "";
}
