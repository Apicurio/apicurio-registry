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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * @author Fabian Martinez
 */
@Path("admin")
public class AdminResourceImpl {

    private static final Logger log = Logger.getLogger(AdminResourceImpl.class.getName());

    private static Level getLogLevel(Logger logger) {
        for (Logger current = logger; current != null;) {
           Level level = current.getLevel();
           if (level != null)
              return level;
           current = current.getParent();
        }
        return Level.INFO;
     }

     @GET
     @Path("logging/{logger}")
     @Produces(MediaType.TEXT_PLAIN)
     public String logger(@PathParam("logger") String loggerName, @QueryParam("level") String level) {
        // get the logger instance
        Logger logger = Logger.getLogger(loggerName);

        // change the log-level if requested
        if (level != null && level.length() > 0) {
            logger.setLevel(Level.parse(level));
            log.info("Changing log level for logger " + loggerName + " to " + level);
        }

        // return the current log-level
        return getLogLevel(logger).getName();
     }

}
