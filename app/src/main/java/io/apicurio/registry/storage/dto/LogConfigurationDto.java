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
package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.LogLevel;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Fabian Martinez
 */
@EqualsAndHashCode
@ToString
public class LogConfigurationDto {

    private String logger;
    private LogLevel logLevel;

    public LogConfigurationDto() {
        // empty
    }

    public LogConfigurationDto(String logger, LogLevel logLevel) {
        this.logger = logger;
        this.logLevel = logLevel;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

}
