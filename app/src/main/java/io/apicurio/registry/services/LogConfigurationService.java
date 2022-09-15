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

package io.apicurio.registry.services;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.LogLevel;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class LogConfigurationService {

    @Inject
    org.slf4j.Logger LOGGER;

    @Inject
    @Current
    RegistryStorage storage;

    @ConfigProperty(name = "quarkus.log.level")
    @Info(category = "log", description = "Log level", availableSince = "2.0.0.Final")
    String defaultLogLevel;

    @Scheduled(concurrentExecution = ConcurrentExecution.SKIP, delayed = "{registry.logconfigjob.delayed}", every = "{registry.logconfigjob.every}")
    public void checkLogLevel() {
        if (!storage.isAlive() || !storage.isReady()) {
            return;
        }
        LOGGER.trace("Running periodic log configuration process");
        for (LogConfigurationDto logConfig : storage.listLogConfigurations()) {
            Logger logger = Logger.getLogger(logConfig.getLogger());
            Level expectedLevel = Level.parse(logConfig.getLogLevel().value());
            if (!getLogLevel(logger).equals(expectedLevel)) {
                LOGGER.info(String.format("Updating logger %s to log level %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
                logger.setLevel(expectedLevel);
            }
            if (logConfig.getLogLevel().value().equals(defaultLogLevel)) {
                LOGGER.info(String.format("Cleaning persisted config for logger %s because default log level is configured %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
                storage.removeLogConfiguration(logConfig.getLogger());
            }
        }
    }

    public List<NamedLogConfiguration> listLogConfigurations() {
        return storage.listLogConfigurations()
                .stream()
                .map(c -> {
                    NamedLogConfiguration lc = new NamedLogConfiguration();
                    lc.setName(c.getLogger());
                    lc.setLevel(c.getLogLevel());
                    return lc;
                })
                .collect(Collectors.toList());
    }

    public NamedLogConfiguration getLogConfiguration(String loggerName) {
        LogConfigurationDto logConfig = null;
        try {
             logConfig = storage.getLogConfiguration(loggerName);
        } catch (LogConfigurationNotFoundException e) {
            //ignored
        }
        Logger targetLogger = Logger.getLogger(loggerName);
        Level actualLevel = getLogLevel(targetLogger);
        if (logConfig == null) {
            NamedLogConfiguration named = new NamedLogConfiguration();
            named.setName(loggerName);
            named.setLevel(LogLevel.fromValue(actualLevel.getName()));
            return named;
        }
        Level expectedLevel = Level.parse(logConfig.getLogLevel().value());
        if (!actualLevel.equals(expectedLevel)) {
            LOGGER.info(String.format("Log configuration not applied, forcing logger update, logger %s to log level %s", logConfig.getLogger(), logConfig.getLogLevel().value()));
            targetLogger.setLevel(expectedLevel);
        }
        NamedLogConfiguration named = new NamedLogConfiguration();
        named.setName(loggerName);
        named.setLevel(logConfig.getLogLevel());
        return named;
    }

    public NamedLogConfiguration setLogLevel(String loggerName, LogLevel logLevel) {
        try {
            LOGGER.info("Changing log level for logger " + loggerName + " to " + logLevel);
            Logger logger = Logger.getLogger(loggerName);
            logger.setLevel(Level.parse(logLevel.value()));
        } catch (Exception e) {
            throw new BadRequestException("level is not a valid log level");
        }

        storage.setLogConfiguration(new LogConfigurationDto(loggerName, logLevel));

        NamedLogConfiguration named = new NamedLogConfiguration();
        named.setName(loggerName);
        named.setLevel(logLevel);
        return named;
    }

    public NamedLogConfiguration removeLogLevelConfiguration(String loggerName) {
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
