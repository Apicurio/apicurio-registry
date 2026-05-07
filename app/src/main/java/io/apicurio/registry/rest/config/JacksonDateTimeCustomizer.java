package io.apicurio.registry.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

@Singleton
public class JacksonDateTimeCustomizer implements ObjectMapperCustomizer {

    private static Logger log = LoggerFactory.getLogger(JacksonDateTimeCustomizer.class);

    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String DEFAULT_DATE_TIME_FORMAT_TZ = "UTC";

    @PostConstruct
    protected void postConstruct() {
        log.debug("Using default REST API date format: {}", DEFAULT_DATE_TIME_FORMAT);
    }

    /**
     * @see io.quarkus.jackson.ObjectMapperCustomizer#customize(com.fasterxml.jackson.databind.ObjectMapper)
     */
    @Override
    public void customize(ObjectMapper mapper) {
        try {
            configureDateFormat(mapper, DEFAULT_DATE_TIME_FORMAT, DEFAULT_DATE_TIME_FORMAT_TZ);
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
