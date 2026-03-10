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

import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Filters REST API requests and responses to generate audit logs for failed requests
 */
@Provider
@Priority(Priorities.RequestResponseFilters.AUDIT)
@ApplicationScoped
public class HttpRequestsAuditFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Context
    HttpServletRequest request;

    @Inject
    AuditHttpRequestContext auditContext;

    @Inject
    AuditLogService auditLog;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        auditContext.setSourceIp(request.getRemoteAddr());
        auditContext.setForwardedFor(
                requestContext.getHeaderString(AuditHttpRequestContext.X_FORWARDED_FOR_HEADER));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        // check if there is already an audit entry for the logic executed in this request
        if (auditContext.isAuditEntryGenerated()) {
            return;
        }

        if (responseContext.getStatus() >= 400) {
            // failed request, generate audit log
            Map<String, String> metadata = new HashMap<>();
            metadata.put("method", requestContext.getMethod());
            metadata.put("path", requestContext.getUriInfo().getPath());
            metadata.put("response_code", String.valueOf(responseContext.getStatus()));
            metadata.put("user", Optional.ofNullable(requestContext.getSecurityContext())
                    .map(SecurityContext::getUserPrincipal).map(Principal::getName).orElseGet(() -> ""));

            auditLog.log("apicurio.audit", "request", AuditHttpRequestContext.FAILURE, metadata, null);
        }
    }
}
