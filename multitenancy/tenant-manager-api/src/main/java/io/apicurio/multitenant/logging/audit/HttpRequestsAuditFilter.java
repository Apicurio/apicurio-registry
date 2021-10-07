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

import io.vertx.core.http.HttpServerRequest;

import org.slf4j.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Filters REST API requests and responses to generate audit logs for failed requests
 *
 * @author Fabian Martinez
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class HttpRequestsAuditFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    Logger log;

    @Context
    HttpServerRequest request;

    @Inject
    AuditHttpRequestContext auditContext;

    @Inject
    AuditLogService auditLog;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        auditContext.setSourceIp(request.remoteAddress().toString());
        auditContext.setForwardedFor(requestContext.getHeaderString(AuditHttpRequestContext.X_FORWARDED_FOR_HEADER));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
        throws IOException {

        // check if there is already an audit entry for the logic executed in this request
        if (auditContext.isAuditEntryGenerated()) {
            return;
        }

        if (responseContext.getStatus() >= 400) {
            //failed request, generate audit log
            Map<String, String> metadata = new HashMap<>();
            metadata.put("method", requestContext.getMethod());
            metadata.put("path", requestContext.getUriInfo().getPath());
            metadata.put("response_code", String.valueOf(responseContext.getStatus()));
            metadata.put("user", Optional.ofNullable(requestContext.getSecurityContext()).map(SecurityContext::getUserPrincipal).map(Principal::getName).orElseGet(() -> ""));

            auditLog.log("request", AuditHttpRequestContext.FAILURE, metadata, null);
        }

    }

}
