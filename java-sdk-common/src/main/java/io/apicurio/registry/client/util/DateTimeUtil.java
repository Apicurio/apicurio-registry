package io.apicurio.registry.client.util;

import com.microsoft.kiota.serialization.ParseNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for parsing date-time values from Kiota ParseNode with support for legacy date formats.
 *
 * This utility provides backward compatibility with Apicurio Registry servers that use non-ISO-8601
 * compliant date formats (e.g., "2025-10-29T21:54:37+0000" instead of "2025-10-29T21:54:37+00:00").
 *
 * The legacy format was used in versions prior to 3.0 when the {@code apicurio.apis.date-format}
 * configuration property was set to {@code yyyy-MM-dd'T'HH:mm:ssZ}.
 *
 * The legacy date format can be configured via:
 * <ul>
 *   <li>System property: {@code -Dapicurio.apis.date-format=yyyy-MM-dd'T'HH:mm:ssZ}</li>
 *   <li>Environment variable: {@code APICURIO_APIS_DATE_FORMAT=yyyy-MM-dd'T'HH:mm:ssZ}</li>
 * </ul>
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/6799">Issue #6799</a>
 */
public class DateTimeUtil {

    private static final String DEFAULT_LEGACY_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String PROPERTY_NAME = "apicurio.apis.date-format";
    private static final String ENV_VAR_NAME = "APICURIO_APIS_DATE_FORMAT";

    /**
     * DateTimeFormatter for parsing legacy date format without colon in timezone offset.
     * The format is configurable via system property or environment variable.
     */
    private static final DateTimeFormatter LEGACY_FORMATTER = createLegacyFormatter();

    /**
     * Creates the DateTimeFormatter for legacy date formats.
     * Checks system property first, then environment variable, then uses default.
     *
     * @return DateTimeFormatter configured with the legacy date format
     */
    private static DateTimeFormatter createLegacyFormatter() {
        // Check system property first (highest priority)
        String format = System.getProperty(PROPERTY_NAME);

        // Fall back to environment variable
        if (format == null || format.isEmpty()) {
            format = System.getenv(ENV_VAR_NAME);
        }
        if (format == null || format.isEmpty()) {
            format = System.getenv(PROPERTY_NAME);
        }

        // Fall back to default
        if (format == null || format.isEmpty()) {
            format = DEFAULT_LEGACY_FORMAT;
        }

        return DateTimeFormatter.ofPattern(format);
    }

    /**
     * Attempts to parse an OffsetDateTime value from a ParseNode with fallback support for legacy formats.
     *
     * This method first attempts to use the standard Kiota ParseNode.getOffsetDateTimeValue() method,
     * which expects ISO-8601 compliant date-time strings. If that fails with a DateTimeParseException,
     * it attempts to parse using a legacy format that was used in older versions of Apicurio Registry.
     *
     * @param parseNode the ParseNode containing the date-time value to parse
     * @return the parsed OffsetDateTime, or null if the parseNode is null or contains a null value
     * @throws DateTimeParseException if the value cannot be parsed using either the standard or legacy format
     */
    public static OffsetDateTime getOffsetDateTimeValue(ParseNode parseNode) {
        if (parseNode == null) {
            return null;
        }

        try {
            // First attempt: use standard ISO-8601 parsing via Kiota
            return parseNode.getOffsetDateTimeValue();
        } catch (DateTimeParseException e) {
            // Second attempt: try legacy format for backward compatibility
            try {
                String dateString = parseNode.getStringValue();
                if (dateString == null) {
                    return null;
                }
                return OffsetDateTime.parse(dateString, LEGACY_FORMATTER);
            } catch (DateTimeParseException legacyException) {
                throw e;
            }
        }
    }
}
