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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.rest.beans.LoggingConfiguration;
import io.apicurio.registry.storage.LoggingConfigurationDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.LogLevel;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
@Logged
public class AdminResourceImpl implements AdminResource {

    private static final Logger LOGGER = Logger.getLogger(AdminResourceImpl.class.getName());

    @Inject
    RegistryStorage storage;

    @ConfigProperty(name = "quarkus.log.level")
    String defaultLogLevel;

    @Scheduled(concurrentExecution = ConcurrentExecution.SKIP, delayed = "{registry.logconfigjob.delayed}", every = "{registry.logconfigjob.every}")
    private void checkLogLevel() {
        LOGGER.fine("Running periodic log configuration process");
        for (LoggingConfigurationDto logConfig : storage.listLoggingConfiguration()) {
            Logger logger = Logger.getLogger(logConfig.getLogger());
            Level expectedLevel = Level.parse(logConfig.getLogLevel().value());
            if (!getLogLevel(logger).equals(expectedLevel)) {
                LOGGER.info(String.format("Updating logger %s to log level %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
                logger.setLevel(expectedLevel);
                if (logConfig.getLogLevel().value().equals(defaultLogLevel)) {
                    LOGGER.info(String.format("Cleaning persisted config for logger %s because default log level is configured %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
                    storage.clearLoggingConfiguration(logConfig.getLogger());
                }
            }
        }
    }

    @Override
    public List<LoggingConfiguration> listLoggingConfigurations() {
        return storage.listLoggingConfiguration()
                .stream()
                .map(c -> new LoggingConfiguration(c.getLogger(), c.getLogLevel()))
                .collect(Collectors.toList());
    }

    @Override
    public LoggingConfiguration getLogLevel(String loggerName) {
        LoggingConfigurationDto logConfig = storage.getLoggingConfiguration(loggerName);
        Logger logger = Logger.getLogger(loggerName);
        Level actualLevel = getLogLevel(logger);
        if (logConfig == null) {
            return new LoggingConfiguration(loggerName, LogLevel.fromValue(actualLevel.getName()));
        }
        Level expectedLevel = Level.parse(logConfig.getLogLevel().value());
        if (!actualLevel.equals(expectedLevel)) {
            LOGGER.info(String.format("Log configuration not applied, forcing logger update, logger %s to log level %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
            logger.setLevel(expectedLevel);
        }
        return new LoggingConfiguration(loggerName, logConfig.getLogLevel());
    }

    @Override
    public LoggingConfiguration setLogLevel(String loggerName, LogLevel level) {

        if (level == null) {
            throw new BadRequestException("level is mandatory");
        }

        try {
            Logger logger = Logger.getLogger(loggerName);
            logger.setLevel(Level.parse(level.value()));
            LOGGER.info("Changing log level for logger " + loggerName + " to " + level);
        } catch ( Exception e ) {
            throw new BadRequestException("level is not a valid log level");
        }

        storage.setLoggingConfiguration(new LoggingConfigurationDto(loggerName, level));

        return new LoggingConfiguration(loggerName, level);
    }

    @Override
    public LoggingConfiguration removeLogLevelConfiguration(String loggerName) {
        return setLogLevel(loggerName, LogLevel.fromValue(defaultLogLevel));
    }

    private Level getLogLevel(Logger logger) {
        for ( Logger current = logger; current != null; ) {
            Level level = current.getLevel();
            if (level != null) {
                return level;
            }
            current = current.getParent();
        }
        return Level.parse(defaultLogLevel);
    }

}
