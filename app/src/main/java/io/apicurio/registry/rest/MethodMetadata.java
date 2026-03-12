package io.apicurio.registry.rest;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for declaring metadata about method parameters that can be extracted by interceptors.
 * <p>
 * This is a generalized version of parameter extraction, allowing interceptors to access method
 * parameters without hardcoding parameter positions.
 * <p>
 * This annotation also acts as an interceptor binding, triggering the {@link MethodMetadataInterceptor}
 * which extracts the parameters and stores them in the invocation context for use by subsequent interceptors.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @MethodMetadata(extractParameters = {"0", "entityId"})
 * @ImmutableCache
 * public Response getContentByGlobalId(long globalId) {
 *     // The interceptor can extract the "globalId" parameter value using key "entityId"
 * }
 * }
 * </pre>
 */
@InterceptorBinding
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface MethodMetadata {

    /**
     * Specifies method parameters to extract as metadata. This field uses pairs of values:
     * <ol>
     *   <li>Position of the parameter (starting at 0) as a String</li>
     *   <li>Key name under which the value should be stored</li>
     * </ol>
     * <p>
     * Example:
     * <pre>
     * {@code
     * extractParameters = {
     *     "0", "id",       // Extract 1st parameter as "id"
     *     "1", "userName"  // Extract 2nd parameter as "userName"
     * }
     * }
     * </pre>
     *
     * @return array of position/key pairs for parameter extraction
     */
    @Nonbinding
    String[] extractParameters() default {};
}
