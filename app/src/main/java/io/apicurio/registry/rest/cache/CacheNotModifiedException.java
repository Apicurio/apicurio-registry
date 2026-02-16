package io.apicurio.registry.rest.cache;

/**
 * Exception thrown when a cached resource has not been modified (If-None-Match matches).
 * This is used for flow control to return 304 Not Modified responses without
 * executing the full method body.
 * <p>
 * This exception is caught by CacheNotModifiedExceptionMapper and converted to a 304 response.
 */
public class CacheNotModifiedException extends RuntimeException {
}
