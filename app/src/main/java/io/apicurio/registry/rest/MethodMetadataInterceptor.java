/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest;

import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * CDI interceptor that extracts method parameters based on {@link MethodMetadata} annotation
 * and stores them in the invocation context for use by subsequent interceptors.
 * <p>
 * This interceptor runs with high priority to ensure parameter data
 * is available to other interceptors.
 * <p>
 * The extracted parameters are stored in the invocation context under the key
 * {@link #EXTRACTED_PARAMETERS_KEY} as a {@code Map<String, Object>}.
 */
@MethodMetadata
@Interceptor
@Priority(Priorities.Interceptors.METHOD_METADATA)
public class MethodMetadataInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MethodMetadataInterceptor.class);

    /**
     * Context data key for storing extracted parameters.
     * Subsequent interceptors can retrieve the Map&lt;String, Object&gt; from the invocation context.
     */
    public static final String EXTRACTED_PARAMETERS_KEY = "io.apicurio.registry.extractedParameters";

    @AroundInvoke
    public Object extractMetadata(InvocationContext context) throws Exception {
        MethodMetadata methodMetadata = context.getMethod().getAnnotation(MethodMetadata.class);

        if (methodMetadata != null && methodMetadata.extractParameters().length > 0) {
            Map<String, Object> extractedParams = new HashMap<>();
            final String[] annotationParams = methodMetadata.extractParameters();

            // Process pairs of (position, key)
            if (annotationParams.length % 2 != 0) {
                throw new IllegalArgumentException("MethodMetadata extractParameters should contain " +
                        "pairs of position and key. Found odd number of elements.");
            }
            for (int i = 0; i <= annotationParams.length - 2; i += 2) {
                String position = annotationParams[i];
                String metadataName = annotationParams[i + 1];

                try {
                    int positionInt = Integer.parseInt(position);
                    Object[] parameters = context.getParameters();

                    if (positionInt < parameters.length) {
                        Object parameterValue = parameters[positionInt];
                        // Allow null values - distinguishes "not extracted" from "extracted but null"
                        extractedParams.put(metadataName, parameterValue);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid parameter position in MethodMetadata: {}", position);
                }
            }

            // Store extracted parameters in context data for subsequent interceptors
            if (!extractedParams.isEmpty()) {
                context.getContextData().put(EXTRACTED_PARAMETERS_KEY, extractedParams);
                log.debug("Extracted {} parameters from method {}",
                        extractedParams.size(), context.getMethod().getName());
            }
        }

        return context.proceed();
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getExtractedParameter(InvocationContext context, String key, Class<T> type) {
        var value = ((Map<String, ?>) context.getContextData().getOrDefault(EXTRACTED_PARAMETERS_KEY, Map.of())).get(key);
        if (type.isInstance(value)) {
            return of(type.cast(value));
        }
        return empty();
    }
}
