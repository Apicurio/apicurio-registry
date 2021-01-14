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
package io.apicurio.registry.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.apicurio.registry.types.LogLevel;

/**
 * Type for Log configuration
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "logger",
    "logLevel"
})
public class LoggingConfiguration {

    /**
     * (Required)
     */
    @JsonProperty("logger")
    private String logger;

    /**
     * (Required)
     */
    @JsonProperty("logLevel")
    private LogLevel logLevel;

    public LoggingConfiguration() {
        //empty
    }

    public LoggingConfiguration(String logger, LogLevel logLevel) {
        this.logger = logger;
        this.logLevel = logLevel;
    }

    @JsonProperty("logger")
    public String getLogger() {
        return logger;
    }

    @JsonProperty("logger")
    public void setLogger(String logger) {
        this.logger = logger;
    }

    @JsonProperty("logLevel")
    public LogLevel getLogLevel() {
        return logLevel;
    }

    @JsonProperty("logLevel")
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

}
