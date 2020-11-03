/*
 * Copyright 2020 Red Hat
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

import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.metrics.LivenessUtil;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.rest.beans.Error;
import io.apicurio.registry.rest.beans.RuleViolationError;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.net.HttpURLConnection.*;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
@Provider
public class RegistryExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(RegistryExceptionMapper.class);

    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;

    private static final Map<Class<? extends Exception>, Integer> CODE_MAP;

    @Inject
    ResponseErrorLivenessCheck liveness;
    @Inject
    LivenessUtil livenessUtil;

    @Context
    HttpServletRequest request;

    static {
        Map<Class<? extends Exception>, Integer> map = new HashMap<>();
        map.put(AlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactNotFoundException.class, HTTP_NOT_FOUND);
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
        map.put(InvalidArtifactTypeException.class, HTTP_BAD_REQUEST);
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    public static Set<Class<? extends Exception>> getIgnored() {
        return CODE_MAP.keySet();
    }

    /**
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(Throwable t) {
        Response.ResponseBuilder builder;
        int code;
        if (t instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) t;
            Response response = wae.getResponse();
            builder = Response.fromResponse(response);
            code = response.getStatus();
        } else {
            code = CODE_MAP.getOrDefault(t.getClass(), HTTP_INTERNAL_ERROR);
            builder = Response.status(code);
        }

        if (code == HTTP_INTERNAL_ERROR) {
            // If the error is not something we should ignore, then we report it to the liveness object 
            // and log it.  Otherwise we only log it if debug logging is enabled.
            if (!livenessUtil.isIgnoreError(t)) {
                liveness.suspectWithException(t);
                log.error(t.getMessage(), t);
            } else {
                if (log.isDebugEnabled()) {
                    log.error(t.getMessage(), t);
                }
            }
        }

        Error error = toError(t, code);
        if (isCompatEndpoint()) {
            error.setDetail(null);
        }
        return builder.type(MediaType.APPLICATION_JSON)
                      .entity(error)
                      .build();
    }

    /**
     * Returns true if the endpoint that caused the error is a "ccompat" endpoint.  If so
     * we need to simplify the error we return.  The apicurio error structure has at least 
     * one additional property.
     */
    private boolean isCompatEndpoint() {
        if (this.request != null) {
            return this.request.getRequestURI().contains("ccompat");
        }
        return false;
    }

    private static Error toError(Throwable t, int code) {
        Error error;

        if (t instanceof RuleViolationException) {
            RuleViolationException rve = (RuleViolationException) t;
            error = new RuleViolationError();
            ((RuleViolationError) error).setCauses(rve.getCauses());
        } else {
            error = new Error();
        }

        error.setErrorCode(code);
        error.setMessage(t.getLocalizedMessage());
        error.setDetail(getStackTrace(t));

        return error;
    }
    
    /**
     * Gets the full stack trace for the given exception and returns it as a
     * string.
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
    
}
