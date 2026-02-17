package io.apicurio.registry.rest.cache.strategy.interceptor;

import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.rest.cache.HttpCachingConfig;
import io.apicurio.registry.rest.cache.strategy.VersionContentCacheStrategy;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import static io.apicurio.registry.rest.MethodMetadataInterceptor.getExtractedParameter;
import static io.apicurio.registry.rest.cache.HttpCaching.caching;
import static io.apicurio.registry.utils.StringUtil.isEmpty;

/**
 * Interceptor that implements HTTP caching for methods annotated with @VersionContentCache.
 * <p>
 * This interceptor runs after MethodMetadataInterceptor and uses the extracted parameters
 * to build a VersionContentCacheStrategy.
 */
@VersionContentCache
@Interceptor
@Priority(Priorities.Interceptors.CACHE)
public class VersionContentCacheInterceptor {

    @Inject
    HttpCachingConfig config;

    @AroundInvoke
    public Object processCaching(InvocationContext context) throws Exception {
        if (config.isCachingEnabled()) {

            VersionContentCache annotation = context.getMethod().getAnnotation(VersionContentCache.class);
            if (annotation == null) {
                throw new UnreachableCodeException();
            }

            var builder = VersionContentCacheStrategy.builder();

            builder.versionExpression(getExtractedParameter(context, annotation.versionExpressionParam(), String.class).orElseThrow(() -> new IllegalStateException("@EntityIdContentCache requires @MethodMetadata with extracted 'entityId' parameter")));

            if (!isEmpty(annotation.referencesParam())) {
                builder.references(getExtractedParameter(context, annotation.referencesParam(), HandleReferencesType.class).orElse(null));
            }

            if (!isEmpty(annotation.refTypeParam())) {
                builder.refType(getExtractedParameter(context, annotation.refTypeParam(), ReferenceType.class).orElse(null));
            }

            var strategy = builder.build();
            caching(strategy).prepare();
        }
        return context.proceed();
    }
}
