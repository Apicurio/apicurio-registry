package io.apicurio.registry.rest.cache.strategy.interceptor;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for HTTP caching using EntityIdContentCacheStrategy.
 * The method must be annotated with @MethodMetadata that extracts an "entityId" parameter.
 * <p>
 * This is used for methods that don't return JAX-RS Response objects but still need HTTP caching.
 * The interceptor will:
 * <ul>
 *   <li>Build EntityIdContentCacheStrategy from extracted parameters</li>
 *   <li>Check If-None-Match header for 304 optimization</li>
 *   <li>Add cache headers to the response</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * {@code
 * @EntityIdCache
 * @MethodMetadata(extractParameters = {"0", MPK_ENTITY_ID})
 * public List<ArtifactReference> referencesByContentId(long contentId) {
 *     ...
 * }
 *
 * @EntityIdCache(referencesParam = "references", returnArtifactTypeParam = "returnArtifactType")
 * @MethodMetadata(extractParameters = {"0", MPK_ENTITY_ID, "1", "references", "2", "returnArtifactType"})
 * public Content getContent(long globalId, HandleReferencesType references, Boolean returnArtifactType) {
 *     ...
 * }
 * }
 * </pre>
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
