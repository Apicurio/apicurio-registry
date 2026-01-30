package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.MethodMetadataInterceptor;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * CDI interceptor that adds HTTP caching headers to JAX-RS Response objects for immutable content.
 *
 * This interceptor is triggered by the {@link ImmutableCache} annotation and implements Phase 1
 * of the HTTP caching strategy. It adds aggressive caching headers to responses for content
 * that never changes once created (globalId, contentId, contentHash endpoints).
 *
 * The interceptor reads extracted method parameters from the invocation context (populated by
 * {@link MethodMetadataInterceptor}) to get the entity ID for the ETag header.
 *
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

    // Immutable content caching: 1 year TTL
    private static final String CACHE_CONTROL_IMMUTABLE = "public, immutable, max-age=31536000";

    // Vary header to ensure cache variations by content negotiation
    private static final String VARY_HEADERS = "Accept, Accept-Encoding";

    // Default key name for entity ID when using MethodMetadata
    public static final String CACHE_KEY_ENTITY_ID = "entityId";

    @AroundInvoke
    public Object addCacheHeaders(InvocationContext context) throws Exception {
        // Execute the original method
        Object result = context.proceed();

        // Only process if the result is a JAX-RS Response
        if (!(result instanceof Response)) {
            return result;
        }

        Response response = (Response) result;

        // Only add cache headers for successful responses (2xx)
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return result;
        }

        // Extract the entity tag using MethodMetadata annotation
        String entityTag = extractEntityTag(context);
        if (entityTag == null) {
            log.warn("Could not extract entity tag from method {} - no MethodMetadata annotation or entityId not found",
                    context.getMethod().getName());
            return result;
        }

        // Build a new Response with the same entity and status, but with added cache headers
        Response.ResponseBuilder builder = Response.fromResponse(response);
        builder.header("Cache-Control", CACHE_CONTROL_IMMUTABLE);
        builder.header("ETag", "\"" + entityTag + "\"");
        builder.header("Vary", VARY_HEADERS);

        log.debug("Added immutable cache headers with ETag: {}", entityTag);

        return builder.build();
    }

    /**
     * Extracts the entity tag value from the invocation context.
     * Parameters that have been extracted by {@link MethodMetadataInterceptor}
     * are read from the invocation context.
     *
     * @param context the invocation context
     * @return the entity tag value, or null if not found
     */
    @SuppressWarnings("unchecked")
    private String extractEntityTag(InvocationContext context) {
        // First, check if parameters were already extracted by MethodMetadataInterceptor
        Object extractedData = context.getContextData().get(MethodMetadataInterceptor.EXTRACTED_PARAMETERS_KEY);
        if (extractedData instanceof Map) {
            Map<String, String> extractedParams = (Map<String, String>) extractedData;
            String entityId = extractedParams.get(CACHE_KEY_ENTITY_ID);
            if (entityId != null) {
                return entityId;
            }
        }

        // Fallback: if no extracted data available, use first parameter (backward compatibility)
        Object[] parameters = context.getParameters();
        if (parameters.length > 0 && parameters[0] != null) {
            return parameters[0].toString();
        }

        return null;
    }
}
