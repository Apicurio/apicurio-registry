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

import io.apicurio.registry.audit.AuditHttpRequestContext;
import io.apicurio.registry.audit.AuditLogService;
import org.slf4j.Logger;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor that executes around methods annotated with {@link Audited}
 * <p>
 * This interceptor follows the execution of a method and marks the audit entry as failed if the inner method throws an exception.
 * <p>
 * This interceptor reads the inner method parameters to gather extra information for the audit entry.
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditedInterceptor {

    @Inject
    AuditLogService auditLogService;

    @Inject
    Logger log;

    @AroundInvoke
    public Object auditMethod(InvocationContext context) throws Exception {

        Audited annotation = context.getMethod().getAnnotation(Audited.class);
        Map<String, String> metadata = new HashMap<>();

        for (Object parameter : context.getParameters()) {
            if (parameter == null || parameter instanceof String) {
                continue;
            }
            Class co = parameter.getClass();
            Field[] cfields = co.getDeclaredFields();
            for(Field f: cfields)
            {
                String attributeName = f.getName();
                String getterMethodName = "get"
                        + attributeName.substring(0, 1).toUpperCase()
                        + attributeName.substring(1);
                Method m;
                try {
                    m = co.getMethod(getterMethodName);
                    Object valObject = m.invoke(parameter);
                    metadata.put(attributeName, valObject != null ? valObject.toString() : "");
                } catch (NoSuchMethodException e) {
                    log.trace("Method not found when extracting metadata for audit logging", e);
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