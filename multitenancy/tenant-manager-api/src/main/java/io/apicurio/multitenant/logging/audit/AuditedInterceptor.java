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

package io.apicurio.multitenant.logging.audit;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Interceptor that executes around methods annotated with {@link Audited}
 *
 * This interceptor follows the execution of a method and marks the audit entry as failed if the inner method throws an exception.
 *
 * This interceptor reads the inner method parameters to gather extra information for the audit entry.
 *
 * *** IMPORTANT NOTE *** TenantId is assumed to be found in the annotated method parameters, either as an Object field or simply as a String.
 * **Caution** Any string parameter will be considered the tenantId
 *
 * @author Fabian Martinez
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
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
            metadata.put("principalId", securityIdentity.getPrincipal().getName());
        }

        for (Object parameter : context.getParameters()) {
            if (parameter instanceof NewRegistryTenantRequest) {
                NewRegistryTenantRequest tenant = (NewRegistryTenantRequest) parameter;
                metadata.put("tenantId", tenant.getTenantId());
                metadata.put("orgId", tenant.getOrganizationId());
                metadata.put("name", tenant.getName());
                metadata.put("createdBy", tenant.getCreatedBy());
            } else if (parameter instanceof String) {
                metadata.put("tenantId", (String)parameter);
            } else if (parameter instanceof UpdateRegistryTenantRequest) {
                UpdateRegistryTenantRequest tenant = (UpdateRegistryTenantRequest) parameter;
                if (tenant.getStatus() != null) {
                    metadata.put("tenantStatus", tenant.getStatus().value());
                }
                if (tenant.getName() != null) {
                    metadata.put("name", tenant.getName());
                }
            }
        }


        String action = annotation.action();
        if (action == null || action.isEmpty()) {
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
            auditLogService.log(action, result, metadata, null);
        }
    }
}