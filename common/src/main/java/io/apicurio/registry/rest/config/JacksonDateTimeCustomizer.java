package io.apicurio.registry.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Configures Jackson ObjectMapper to use ISO-8601 date format for REST API responses.
 * This ensures compatibility with generated client SDKs that expect standard date-time formats.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/6799">Issue #6799</a>
 */
@Singleton
public class JacksonDateTimeCustomizer implements ObjectMapperCustomizer {

    private static final Logger log = LoggerFactory.getLogger(JacksonDateTimeCustomizer.class);

    /**
     * ISO-8601 compliant date-time format with UTC timezone.
     * Format: yyyy-MM-dd'T'HH:mm:ss'Z'
     * Example: 2026-03-27T02:15:00Z
     */
    private static final String ISO_8601_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String UTC_TIMEZONE = "UTC";

    /**
     * Configures the ObjectMapper to use ISO-8601 date format.
     *
     * @param mapper the ObjectMapper to customize
     */
    @Override
    public void customize(ObjectMapper mapper) {
        log.debug("Configuring REST API to use ISO-8601 date format: {}", ISO_8601_DATE_TIME_FORMAT);
        SimpleDateFormat dateFormat = new SimpleDateFormat(ISO_8601_DATE_TIME_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone(UTC_TIMEZONE));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setDateFormat(dateFormat);
    }

}
