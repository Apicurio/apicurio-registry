/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.logging.audit;

import io.apicurio.registry.rest.MethodMetadataInterceptor;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor that executes around methods annotated with {@link Audited}
 * <p>
 * This interceptor follows the execution of a method and marks the audit entry as failed if the inner method
 * throws an exception.
 * <p>
 * This interceptor reads extracted method parameters from the invocation context (populated by
 * {@link MethodMetadataInterceptor}) to gather extra information for the audit entry.
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION - 100)
// Runs before other application interceptors, e.g. *PermissionInterceptor
public class AuditedInterceptor {

    @Inject
    AuditLogService auditLogService;

    @Inject
    Instance<SecurityIdentity> securityIdentity;

    @Inject
    Instance<AuditMetaDataExtractor> extractors;

    @AroundInvoke
    public Object auditMethod(InvocationContext context) throws Exception {

        Audited annotation = context.getMethod().getAnnotation(Audited.class);
        Map<String, String> metadata = new HashMap<>();

        if (securityIdentity.isResolvable() && !securityIdentity.get().isAnonymous()) {
            metadata.put(AuditingConstants.KEY_PRINCIPAL_ID, securityIdentity.get().getPrincipal().getName());
        }

        extractMetaData(context, metadata);

        String action = annotation.action();
        if (action.isEmpty()) {
            action = context.getMethod().getName();
        }

        String result = AuditHttpRequestContext.SUCCESS;
        try {
            return context.proceed();
        } catch (Exception e) {
            result = AuditHttpRequestContext.FAILURE;
            metadata.put("error_msg", e.getMessage());
            throw e;
        } finally {
            auditLogService.log("apicurio.audit", action, result, metadata, null);
        }
    }

    /**
     * Extracts metadata from the context. Parameters that have been extracted by
     * {@link MethodMetadataInterceptor} are read from the invocation context.
     *
     * @param context the invocation context
     * @param metadata the map to populate with extracted metadata
     */
    @SuppressWarnings("unchecked")
    protected void extractMetaData(InvocationContext context, Map<String, String> metadata) {
        // First, check if parameters were already extracted by MethodMetadataInterceptor
        Object extractedData = context.getContextData().get(io.apicurio.registry.rest.MethodMetadataInterceptor.EXTRACTED_PARAMETERS_KEY);
        if (extractedData instanceof Map) {
            Map<String, String> extractedParams = (Map<String, String>) extractedData;
            metadata.putAll(extractedParams);
        } else if (extractors.iterator().hasNext()) {
            // No extracted data available. Fallback to using any configured extractors.
            // Extract metadata from the collection of params on the context.
            for (Object parameter : context.getParameters()) {
                for (AuditMetaDataExtractor extractor : extractors) {
                    if (extractor.accept(parameter)) {
                        extractor.extractMetaDataInto(parameter, metadata);
                    }
                }
            }
        }
    }

}