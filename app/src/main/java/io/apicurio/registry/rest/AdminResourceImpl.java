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

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.BadRequestException;

import io.apicurio.registry.logging.Logged;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
@Logged
public class AdminResourceImpl implements AdminResource {

    private static final Logger log = Logger.getLogger(AdminResourceImpl.class.getName());

    private static Level getLogLevel(Logger logger) {
        for ( Logger current = logger; current != null; ) {
            Level level = current.getLevel();
            if (level != null) {
                return level;
            }
            current = current.getParent();
        }
        return Level.INFO;
    }

    @Override
    public String getLogLevel(String loggerName) {
        return getLogLevel(Logger.getLogger(loggerName)).getName();
    }

    @Override
    public String setLogLevel(String loggerName, String level) {

        if (level == null || level.isEmpty()) {
            throw new BadRequestException("level is mandatory");
        }

        Logger logger = Logger.getLogger(loggerName);

        try {
            logger.setLevel(Level.parse(level));
            log.info("Changing log level for logger " + loggerName + " to " + level);
        } catch (Exception e) {
            throw new BadRequestException("level is not a valid log level");
        }

        return getLogLevel(logger).getName();
    }

}
