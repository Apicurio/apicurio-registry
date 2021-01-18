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

import io.apicurio.registry.rest.beans.LoggingConfiguration;
import io.apicurio.registry.storage.LoggingConfigurationDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.LogLevel;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class AdminResourceImpl implements AdminResource {

    private static final Logger LOGGER = Logger.getLogger(AdminResourceImpl.class.getName());

    @Inject
    @Current
    RegistryStorage storage;

    @ConfigProperty(name = "quarkus.log.level")
    String defaultLogLevel;

    @Scheduled(concurrentExecution = ConcurrentExecution.SKIP, delayed = "{registry.logconfigjob.delayed}", every = "{registry.logconfigjob.every}")
    public void checkLogLevel() {
        LOGGER.fine("Running periodic log configuration process");
        for (LoggingConfigurationDto logConfig : storage.listLoggingConfigurations()) {
            Logger logger = Logger.getLogger(logConfig.getLogger());
            Level expectedLevel = Level.parse(logConfig.getLogLevel().value());
            if (!getLogLevel(logger).equals(expectedLevel)) {
                LOGGER.info(String.format("Updating logger %s to log level %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
                logger.setLevel(expectedLevel);
            }
            if (logConfig.getLogLevel().value().equals(defaultLogLevel)) {
                LOGGER.info(String.format("Cleaning persisted config for logger %s because default log level is configured %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
                storage.clearLoggingConfiguration(logConfig.getLogger());
            }
        }
    }

    @Override
    public List<LoggingConfiguration> listLoggingConfigurations() {
        return storage.listLoggingConfigurations()
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
    public LoggingConfiguration setLogLevel(LoggingConfiguration loggingConfiguration) {

        if (loggingConfiguration == null || loggingConfiguration.getLogger() == null || loggingConfiguration.getLogLevel() == null) {
            throw new BadRequestException("Missing parameters");
        }

        try {
            Logger logger = Logger.getLogger(loggingConfiguration.getLogger());
            logger.setLevel(Level.parse(loggingConfiguration.getLogLevel().value()));
            LOGGER.info("Changing log level for logger " + loggingConfiguration.getLogger() + " to " + loggingConfiguration.getLogLevel());
        } catch ( Exception e ) {
            throw new BadRequestException("level is not a valid log level");
        }

        storage.setLoggingConfiguration(new LoggingConfigurationDto(loggingConfiguration.getLogger(), loggingConfiguration.getLogLevel()));

        return loggingConfiguration;
    }

    @Override
    public LoggingConfiguration removeLogLevelConfiguration(String loggerName) {
        return setLogLevel(new LoggingConfiguration(loggerName, LogLevel.fromValue(defaultLogLevel)));
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
