package io.apicurio.registry.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.common.apps.config.Info;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_API;

@Singleton
public class JacksonDateTimeCustomizer implements ObjectMapperCustomizer {

    private static Logger log = LoggerFactory.getLogger(JacksonDateTimeCustomizer.class);

    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String DEFAULT_DATE_TIME_FORMAT_TZ = "UTC";

    /**
     * @deprecated This configuration property is deprecated and will be removed in a future release.
     *             Customizing the date format causes incompatibility with generated client SDKs.
     *             Use the default ISO-8601 format. See issue #6799.
     */
    @Deprecated(since = "3.1.2")
    @ConfigProperty(name = "apicurio.apis.date-format", defaultValue = DEFAULT_DATE_TIME_FORMAT)
    @Info(category = CATEGORY_API, description = "API date format (DEPRECATED - do not use)", availableSince = "2.4.3.Final")
    String dateFormat;

    /**
     * @deprecated This configuration property is deprecated and will be removed in a future release.
     *             Customizing the date format causes incompatibility with generated client SDKs.
     *             Use the default ISO-8601 format. See issue #6799.
     */
    @Deprecated(since = "3.1.2")
    @ConfigProperty(name = "apicurio.apis.date-format-timezone", defaultValue = DEFAULT_DATE_TIME_FORMAT_TZ)
    @Info(category = CATEGORY_API, description = "API date format timezone (DEPRECATED - do not use)", availableSince = "2.4.3.Final")
    String timezone;

    @PostConstruct
    protected void postConstruct() {
        // Check if custom date format configuration is being used
        boolean isCustomFormat = !DEFAULT_DATE_TIME_FORMAT.equals(dateFormat);
        boolean isCustomTimezone = !DEFAULT_DATE_TIME_FORMAT_TZ.equals(timezone);

        if (isCustomFormat || isCustomTimezone) {
            log.warn("=====================================================================");
            log.warn("DEPRECATION WARNING: Custom date format configuration detected!");
            log.warn("");
            log.warn("The following configuration properties are DEPRECATED and will be");
            log.warn("removed in a future release:");
            log.warn("  - apicurio.apis.date-format");
            log.warn("  - apicurio.apis.date-format-timezone");
            log.warn("");
            log.warn("Current configuration:");
            log.warn("  Date format: " + dateFormat);
            log.warn("  Timezone: " + timezone);
            log.warn("");
            log.warn("Customizing the date format causes incompatibility issues with");
            log.warn("generated client SDKs that expect ISO-8601 compliant date-time");
            log.warn("formats as specified by the OpenAPI specification.");
            log.warn("");
            log.warn("Please remove these custom configurations and use the default");
            log.warn("ISO-8601 format.");
            log.warn("=====================================================================");
        } else {
            log.debug("---------------------------------------------------------------------");
            log.debug("Using default REST API date format: " + dateFormat);
            log.debug("---------------------------------------------------------------------");
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
