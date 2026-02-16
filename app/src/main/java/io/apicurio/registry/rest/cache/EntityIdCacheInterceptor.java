package io.apicurio.registry.rest.cache;

import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.rest.cache.strategy.EntityIdContentCacheStrategy;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import static io.apicurio.registry.rest.MethodMetadataInterceptor.getExtractedParameter;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_ENTITY_ID;
import static io.apicurio.registry.rest.cache.HttpCaching.caching;
import static io.apicurio.registry.utils.StringUtil.isEmpty;

/**
 * Interceptor that implements HTTP caching for methods annotated with @EntityIdCache.
 * <p>
 * This interceptor runs after MethodMetadataInterceptor and uses the extracted parameters
 * to build an EntityIdContentCacheStrategy. It handles:
 * <ul>
 *   <li>Checking If-None-Match header for 304 Not Modified responses</li>
 *   <li>Storing cache strategy for response filter to add cache headers</li>
 * </ul>
 * <p>
 * The method must be annotated with @MethodMetadata that extracts an "entityId" parameter.
 */
@EntityIdCache
@Interceptor
@Priority(Priorities.Interceptors.CACHE)
public class EntityIdCacheInterceptor {

    @Inject
    HttpCachingConfig config;

    @AroundInvoke
    public Object processCaching(InvocationContext context) throws Exception {

        if (!config.isCachingEnabled()) {
            return context.proceed();
        }

        EntityIdCache annotation = context.getMethod().getAnnotation(EntityIdCache.class);
        if (annotation == null) {
            throw new UnreachableCodeException();
        }

        var builder = EntityIdContentCacheStrategy.builder();

        builder.entityId(getExtractedParameter(context, MPK_ENTITY_ID, Object.class).orElseThrow(() -> new IllegalStateException("@EntityIdCache requires @MethodMetadata with extracted 'entityId' parameter")));

        if (!isEmpty(annotation.referencesParam())) {
            builder.references(getExtractedParameter(context, annotation.referencesParam(), HandleReferencesType.class).orElse(null));
        }

        if (!isEmpty(annotation.returnArtifactTypeParam())) {
            builder.returnArtifactType(getExtractedParameter(context, annotation.returnArtifactTypeParam(), Boolean.class).orElse(null));
        }

        var strategy = builder.build();

        caching(strategy).prepare();
        return context.proceed();
    }
}
