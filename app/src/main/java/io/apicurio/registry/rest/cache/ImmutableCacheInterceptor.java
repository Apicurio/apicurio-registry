package io.apicurio.registry.rest.cache;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.rest.MethodMetadataInterceptor;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_CACHE;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_ENTITY_ID;

/**
 * CDI interceptor that adds HTTP caching headers to JAX-RS Response objects for immutable content.
 * <p>
 * This interceptor is triggered by the {@link ImmutableCache} annotation and implements Phase 1
 * of the HTTP caching strategy. It adds aggressive caching headers to responses for content
 * that never changes once created (globalId, contentId, contentHash endpoints).
 * <p>
 * The interceptor reads extracted method parameters from the invocation context (populated by
 * {@link MethodMetadataInterceptor}) to get the entity ID for the ETag header.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @ImmutableCache
 * @MethodMetadata(extractParameters = {"0", "entityId"})
 * public Response getContentById(long contentId) {
 *     // ...
 * }
 * }
 * </pre>
 */
@ImmutableCache
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class ImmutableCacheInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ImmutableCacheInterceptor.class);

    @ConfigProperty(name = "apicurio.http-caching.highly-cacheable.max-age-seconds", defaultValue = "31536000" /* 1 year */)
    @Info(category = CATEGORY_CACHE, description = "HTTP cache expiration for highly cacheable REST API endpoints, in seconds. " +
            "If set to 0, caching is disabled.", availableSince = "3.1.8")
    Long expirationSeconds;

    private static final String CACHE_CONTROL_IMMUTABLE = "public, immutable, max-age=%s";

    // Vary header to ensure cache variations by content negotiation
    private static final String VARY_HEADERS = "Accept, Accept-Encoding";

    @AroundInvoke
    public Object addCacheHeaders(InvocationContext context) throws Exception {
        // Execute the original method
        Object result = context.proceed();

        if (expirationSeconds <= 0) {
            log.debug("Skipping adding cache headers as `apicurio.http-caching.highly-cacheable.max-age-seconds` is set to {}", expirationSeconds);
            return result;
        }

        // Only process if the result is a JAX-RS Response
        if (!(result instanceof Response response)) {
            return result;
        }

        // Only add cache headers for successful responses (2xx)
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return result;
        }

        // Extract the entity tag using MethodMetadata annotation
        var entityTag = extractEntityTag(context);
        if (entityTag == null) {
            throw new IllegalArgumentException("Could not extract entity tag from method %s"
                    .formatted(context.getMethod().getName()));
        }

        // Build a new Response with the same entity and status, but with added cache headers
        Response.ResponseBuilder builder = Response.fromResponse(response);
        builder.header("Cache-Control", CACHE_CONTROL_IMMUTABLE.formatted(expirationSeconds));
        builder.header("ETag", "\"" + entityTag + "\"");
        builder.header("Vary", VARY_HEADERS);

        log.debug("Added immutable cache headers with ETag: {}", entityTag);

        return builder.build();
    }

    /**
     * Extracts the entity tag value from the invocation context.
     * Parameters that have been extracted by {@link MethodMetadataInterceptor}
     * are read from the invocation context and converted to String for the ETag.
     *
     * @param context the invocation context
     * @return the entity tag value, or null if not found
     */
    @SuppressWarnings("unchecked")
    private Object extractEntityTag(InvocationContext context) {
        // First, check if parameters were already extracted by MethodMetadataInterceptor
        Object extractedData = context.getContextData().get(MethodMetadataInterceptor.EXTRACTED_PARAMETERS_KEY);
        if (extractedData instanceof Map) {
            Map<String, Object> extractedParams = (Map<String, Object>) extractedData;
            return extractedParams.get(MPK_ENTITY_ID);
        }
        return null;
    }
}
