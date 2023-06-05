/*
 * Copyright 2023 Red Hat Inc
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

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkus.jackson.ObjectMapperCustomizer;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class JacksonDateTimeCustomizer implements ObjectMapperCustomizer {
    
    @Inject
    Logger log;
    
    @ConfigProperty(name = "registry.apis.v2.date-format", defaultValue = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    String dateFormat;
    @ConfigProperty(name = "registry.apis.v2.date-format-timezone", defaultValue = "UTC")
    String timezone;

    @PostConstruct
    protected void postConstruct() {
        log.info("Setting REST API date format to: {}", dateFormat);
    }

    public void customize(ObjectMapper mapper) {
        try {
            configureDateFormat(mapper, dateFormat, timezone);
        } catch (Exception e) {
            log.error("Error setting REST API date format.", e);
            configureDateFormat(mapper, "yyyy-MM-dd'T'HH:mm:ss'Z'", "UTC");
        }
    }

    protected static void configureDateFormat(ObjectMapper mapper, String format, String tz) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        df.setTimeZone(TimeZone.getTimeZone(tz));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setDateFormat(df);
    }
    
}
