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



import io.quarkus.security.identity.SecurityIdentity;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_PRINCIPAL_ID;

/**
 * Interceptor that executes around methods annotated with {@link Audited}
 * <p>
 * This interceptor follows the execution of a method and marks the audit entry as failed if the inner method throws an exception.
 * <p>
 * This interceptor reads the inner method parameters to gather extra information for the audit entry.
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION - 100)
// Runs before other application interceptors, e.g. *PermissionInterceptor
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
            metadata.put(KEY_PRINCIPAL_ID, securityIdentity.getPrincipal().getName());
        }

        final String[] annotationParams = annotation.extractParameters();
        if (annotationParams.length > 0) {
            for (int i = 0; i <= annotationParams.length - 2; i += 2) {
                Object parameterValue = context.getParameters()[Integer.parseInt(annotationParams[i])];
                if (parameterValue != null) {
                    metadata.put(annotationParams[i + 1], parameterValue.toString());
                }
            }
        }

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
            auditLogService.log("registry.audit", action, result, metadata, null);
        }
    }
}