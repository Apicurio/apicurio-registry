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
package io.apicurio.registry.rest;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.apicurio.registry.rest.beans.LoggingConfiguration;

/**
 * JAX-RS interface for the admin API of Apicurio Registry.
 */
@Path("admin")
public interface AdminResource {

    /**
     * Returns the list of persisted logging configurations
     *
     * @return the list of persisted logging configurations
     */
    @GET
    @Path("logging")
    @Produces(MediaType.APPLICATION_JSON)
    List<LoggingConfiguration> listLoggingConfigurations();

    /**
     * Returns the configured log level for the provided loggerName, if no logging configuration is persisted it will return the current log level in the system.
     *
     * @param loggerName
     * @param level
     * @return log level
     */
    @GET
    @Path("logging/{logger}")
    @Produces(MediaType.APPLICATION_JSON)
    LoggingConfiguration getLogLevel(@PathParam("logger") String loggerName);

    /**
     * Configures the logger referenced by the provided loggerName with the given log level and returns the configured log level.
     *
     * Returns http status code 400 if level is not provided.
     *
     * @param loggerName
     * @param level
     * @return log level
     */
    @PUT
    @Path("logging/")
    @Produces(MediaType.APPLICATION_JSON)
    LoggingConfiguration setLogLevel(LoggingConfiguration loggingConfiguration);

    /**
     * Removes the configured log level (if any) for the given logger
     *
     * @param loggerName
     * @return the resulting configuration (the default log level)
     */
    @DELETE
    @Path("logging/{logger}")
    @Produces(MediaType.APPLICATION_JSON)
    LoggingConfiguration removeLogLevelConfiguration(@PathParam("logger") String loggerName);

}
