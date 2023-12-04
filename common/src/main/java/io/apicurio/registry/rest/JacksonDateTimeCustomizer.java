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


@Singleton
public class JacksonDateTimeCustomizer implements ObjectMapperCustomizer {
    
    private static Logger log = LoggerFactory.getLogger(JacksonDateTimeCustomizer.class);
    
    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String DEFAULT_DATE_TIME_FORMAT_TZ = "UTC";
    
    @ConfigProperty(name = "registry.apis.v2.date-format", defaultValue = DEFAULT_DATE_TIME_FORMAT)
    @Info(category = "api", description = "API date format", availableSince = "2.4.3.Final")
    String dateFormat;
    @ConfigProperty(name = "registry.apis.v2.date-format-timezone", defaultValue = DEFAULT_DATE_TIME_FORMAT_TZ)
    @Info(category = "api", description = "API date format (TZ)", availableSince = "2.4.3.Final")
    String timezone;

    @PostConstruct
    protected void postConstruct() {
        log.debug("---------------------------------------------------------------------");
        log.debug("Overriding REST API date format.  Using: " + dateFormat);
        log.debug("---------------------------------------------------------------------");
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
