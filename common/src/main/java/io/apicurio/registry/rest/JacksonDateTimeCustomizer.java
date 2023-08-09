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

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.apicurio.common.apps.config.Info;
import io.quarkus.jackson.ObjectMapperCustomizer;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class JacksonDateTimeCustomizer implements ObjectMapperCustomizer {
    
    private static Logger log = LoggerFactory.getLogger(JacksonDateTimeCustomizer.class);
    
    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String DEFAULT_DATE_TIME_FORMAT_TZ = "UTC";
    
    @ConfigProperty(name = "registry.apis.v2.date-format", defaultValue = DEFAULT_DATE_TIME_FORMAT)
    @Info(category = "api", description = "API date format", availableSince = "2.4.3.Final")
    String dateFormat;
    @ConfigProperty(name = "registry.apis.v2.date-format-timezone", defaultValue = DEFAULT_DATE_TIME_FORMAT_TZ)
    @Info(category = "api", description = "API date format (TZ)", availableSince = "2.4.3.Final")
    String timezone;

    @PostConstruct
    protected void postConstruct() {
        if (dateFormat.equals("yyyy-MM-dd'T'HH:mm:ssZ")) {
            log.info("---------------------------------------------------------------------");
            log.info("Legacy REST API date formats enabled (this is currently the default).");
            log.info("");
            log.info("For maximum compatibility and to ease upgrades from older versions");
            log.info("of Registry, the date format used in the REST API is not compliant");
            log.info("with OpenAPI standards (due to a bug in older versions).  Please");
            log.info("make sure you upgrade all of your client applications to use the");
            log.info("latest client version.  The next release will fix the date format");
            log.info("bug, which will result in older clients no longer being compatible");
            log.info("with the REST API.");
            log.info("");
            log.info("If you would like to fix the date format bug in THIS version of");
            log.info("Registry (great!) please set the following ENV variable + value:");
            log.info("");
            log.info("REGISTRY_APIS_V2_DATE_FORMAT=yyyy-MM-dd'T'HH:mm:ss'Z'");
            log.info("");
            log.info("Doing this will result in a REST API that is OpenAPI compliant, but");
            log.info("please remember to upgrade all your client applications first!");
            log.info("---------------------------------------------------------------------");
        } else {
            log.info("---------------------------------------------------------------------");
            log.info("Overriding REST API date format.  Using: " + dateFormat);
            log.info("---------------------------------------------------------------------");
        }
    }

    /**
     * @see io.quarkus.jackson.ObjectMapperCustomizer#customize(com.fasterxml.jackson.databind.ObjectMapper)
     */
    @Override
    public void customize(ObjectMapper mapper) {
        try {
            configureDateFormat(mapper, dateFormat, timezone);
        } catch (Exception e) {
            log.error("Error setting REST API date format.", e);
            configureDateFormat(mapper, DEFAULT_DATE_TIME_FORMAT, DEFAULT_DATE_TIME_FORMAT_TZ);
        }
    }

    protected static void configureDateFormat(ObjectMapper mapper, String format, String tz) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        df.setTimeZone(TimeZone.getTimeZone(tz));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setDateFormat(df);
    }
    
}
