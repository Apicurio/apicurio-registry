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
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor that executes around methods annotated with {@link Audited}
 * <p>
 * This interceptor follows the execution of a method and marks the audit entry as failed if the inner method
 * throws an exception.
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

        extractMetaData(context, annotation, metadata);

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
     * Extracts metadata from the context based on the "extractParameters" configuration of the "Audited"
     * annotation.
     *
     * @param context
     * @param annotation
     * @param metadata
     */
    protected void extractMetaData(InvocationContext context, Audited annotation,
            Map<String, String> metadata) {
        final String[] annotationParams = annotation.extractParameters();
        if (annotationParams.length > 0) {
            for (int i = 0; i <= annotationParams.length - 2; i += 2) {
                String position = annotationParams[i];
                String metadataName = annotationParams[i + 1];

                String propertyName = null;
                if (position.contains(".")) {
                    position = extractPosition(position);
                    propertyName = extractPropertyName(position);
                }
                int positionInt = Integer.parseInt(position);

                Object parameterValue = context.getParameters()[positionInt];
                if (parameterValue != null && propertyName != null) {
                    parameterValue = getPropertyValueFromParam(parameterValue, propertyName);
                }
                if (parameterValue != null) {
                    metadata.put(metadataName, parameterValue.toString());
                }
            }
        } else if (extractors.iterator().hasNext()) {
            // No parameters defined on the annotation. So try to use any configured
            // extractors instead. Extract metadata from the collection of params on
            // the context.
            for (Object parameter : context.getParameters()) {
                for (AuditMetaDataExtractor extractor : extractors) {
                    if (extractor.accept(parameter)) {
                        extractor.extractMetaDataInto(parameter, metadata);
                    }
                }
            }
        }
    }

    private String extractPosition(String position) {
        return position.split(".")[0];
    }

    private String extractPropertyName(String position) {
        return position.split(".")[1];
    }

    private String getPropertyValueFromParam(Object parameterValue, String propertyName) {
        try {
            return BeanUtils.getProperty(parameterValue, propertyName);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return parameterValue.toString();
        }
    }

}