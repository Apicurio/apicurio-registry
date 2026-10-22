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
import io.apicurio.registry.util.Priorities;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.rest.MethodMetadataInterceptor.getExtractedParameters;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_PRINCIPAL_ID;
import static java.lang.String.valueOf;

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
@Priority(Priorities.Interceptors.AUDIT)
public class AuditedInterceptor {

    @Inject
    AuditLogService auditLogService;

    @Inject
    SecurityIdentity securityIdentity;

    @AroundInvoke
    public Object auditMethod(InvocationContext context) throws Exception {

        Audited annotation = context.getMethod().getAnnotation(Audited.class);
        Map<String, String> metadata = new HashMap<>();

        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            metadata.put(MPK_PRINCIPAL_ID, securityIdentity.getPrincipal().getName());
        }

        getExtractedParameters(context).ifPresent(eps -> {
            eps.forEach((k, v) -> metadata.put(k, valueOf(v)));
        });

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
}