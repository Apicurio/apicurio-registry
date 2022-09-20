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

package io.apicurio.registry.services.http;

import io.apicurio.common.apps.config.Info;
import io.apicurio.tenantmanager.client.exception.TenantManagerClientException;
import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.mt.TenantForbiddenException;
import io.apicurio.registry.mt.TenantNotAuthorizedException;
import io.apicurio.registry.mt.TenantNotFoundException;
import io.apicurio.registry.mt.limits.LimitExceededException;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.ParametersConflictException;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rest.v2.beans.RuleViolationCause;
import io.apicurio.registry.rest.v2.beans.RuleViolationError;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import io.apicurio.registry.storage.AlreadyExistsException;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ConfigPropertyNotFoundException;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.DownloadNotFoundException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactIdException;
import io.apicurio.registry.storage.InvalidArtifactStateException;
import io.apicurio.registry.storage.InvalidArtifactTypeException;
import io.apicurio.registry.storage.InvalidGroupIdException;
import io.apicurio.registry.storage.InvalidPropertyValueException;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.RoleMappingAlreadyExistsException;
import io.apicurio.registry.storage.RoleMappingNotFoundException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionAlreadyExistsException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.smallrye.mutiny.TimeoutException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import static java.net.HttpURLConnection.*;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class RegistryExceptionMapperService {

    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;

    private static final Map<Class<? extends Exception>, Integer> CODE_MAP;

    @Inject
    Logger log;

    @Inject
    ResponseErrorLivenessCheck liveness;

    @Inject
    LivenessUtil livenessUtil;

    @ConfigProperty(name = "registry.api.errors.include-stack-in-response", defaultValue = "false")
    @Info(category = "api", description = "Include stack trace in errors responses", availableSince = "2.1.4.Final")
    boolean includeStackTrace;

    static {
        Map<Class<? extends Exception>, Integer> map = new HashMap<>();
        map.put(AlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(VersionAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ContentNotFoundException.class, HTTP_NOT_FOUND);
        map.put(BadRequestException.class, HTTP_BAD_REQUEST);
        map.put(InvalidArtifactStateException.class, HTTP_BAD_REQUEST);
        map.put(NotFoundException.class, HTTP_NOT_FOUND);
        map.put(RuleAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(RuleNotFoundException.class, HTTP_NOT_FOUND);
        map.put(RuleViolationException.class, HTTP_CONFLICT);
        map.put(DefaultRuleDeletionException.class, HTTP_CONFLICT);
        map.put(VersionNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ConflictException.class, HTTP_CONFLICT);
        map.put(UnprocessableEntityException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(UnprocessableSchemaException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(InvalidArtifactTypeException.class, HTTP_BAD_REQUEST);
        map.put(InvalidArtifactIdException.class, HTTP_BAD_REQUEST);
        map.put(InvalidPropertyValueException.class, HTTP_BAD_REQUEST);
        map.put(InvalidGroupIdException.class, HTTP_BAD_REQUEST);
        map.put(MissingRequiredParameterException.class, HTTP_BAD_REQUEST);
        map.put(LogConfigurationNotFoundException.class, HTTP_NOT_FOUND);
        map.put(GroupNotFoundException.class, HTTP_NOT_FOUND);
        map.put(LimitExceededException.class, HTTP_CONFLICT);
        map.put(TenantNotAuthorizedException.class, HTTP_FORBIDDEN);
        map.put(TenantForbiddenException.class, HTTP_FORBIDDEN);
        map.put(RoleMappingAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(RoleMappingNotFoundException.class, HTTP_NOT_FOUND);
        map.put(TenantManagerClientException.class, HTTP_INTERNAL_ERROR);
        map.put(ParametersConflictException.class, HTTP_CONFLICT);
        map.put(DownloadNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ConfigPropertyNotFoundException.class, HTTP_NOT_FOUND);
        // From io.apicurio.registry.mt.TenantMetadataService:
        map.put(NotAuthorizedException.class, HTTP_FORBIDDEN);
        map.put(ForbiddenException.class, HTTP_FORBIDDEN);
        // Not using HTTP_NOT_FOUND to prevent leaking information by scanning for existing tenants
        map.put(TenantNotFoundException.class, HTTP_FORBIDDEN);
        map.put(TimeoutException.class, HTTP_UNAVAILABLE);
        // TODO Merge this list with io.apicurio.registry.rest.RegistryExceptionMapper
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    public static Set<Class<? extends Exception>> getIgnored() {
        return CODE_MAP.keySet();
    }

    public ErrorHttpResponse mapException(Throwable t) {
        int code;
        Response response = null;
        if (t instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) t;
            response = wae.getResponse();
            code = response.getStatus();
        } else {
            code = CODE_MAP.getOrDefault(t.getClass(), HTTP_INTERNAL_ERROR);
        }

        if (code == HTTP_INTERNAL_ERROR) {
            // If the error is not something we should ignore, then we report it to the liveness object
            // and log it.  Otherwise we only log it if debug logging is enabled.
            if (!livenessUtil.isIgnoreError(t)) {
                liveness.suspectWithException(t);
            }
            log.error("[500 ERROR DETECTED] : " + t.getMessage(), t);
        }

        Error error = toError(t, code);

        return new ErrorHttpResponse(code, error, response);

    }

    private Error toError(Throwable t, int code) {
        Error error;

        if (t instanceof RuleViolationException) {
            RuleViolationException rve = (RuleViolationException) t;
            error = new RuleViolationError();
            ((RuleViolationError) error).setCauses(toRestCauses(rve.getCauses()));
        } else {
            error = new Error();
        }

        error.setErrorCode(code);
        error.setMessage(t.getLocalizedMessage());
        if (includeStackTrace) {
            error.setDetail(getStackTrace(t));
        } else {
            error.setDetail(getRootMessage(t));
        }
        error.setName(t.getClass().getSimpleName());
        return error;
    }

    /**
     * Converts rule violations to appropriate error beans.
     *
     * @param violations
     */
    private List<RuleViolationCause> toRestCauses(Set<RuleViolation> violations) {
        if (violations == null) {
            return null;
        }
        return violations.stream().map(violation -> {
            RuleViolationCause cause = new RuleViolationCause();
            cause.setContext(violation.getContext());
            cause.setDescription(violation.getDescription());
            return cause;
        }).collect(Collectors.toList());
    }

    /**
     * Gets the full stack trace for the given exception and returns it as a
     * string.
     *
     * @param t
     */
    private static String getStackTrace(Throwable t) {
        try (StringWriter writer = new StringWriter()) {
            t.printStackTrace(new PrintWriter(writer));
            return writer.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getRootMessage(Throwable t) {
        return ExceptionUtils.getRootCauseMessage(t);
    }

}
