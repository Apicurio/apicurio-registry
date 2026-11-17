package io.apicurio.registry.logging;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConsoleLogFormatTest {

    private static final Pattern DATE_PATTERN = Pattern.compile("%d\\{([^}]+)\\}");

    @Test
    public void testConsoleLogFormatDatePattern() throws Exception {
        Properties properties = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            Assertions.assertNotNull(is, "application.properties should be present on classpath");
            properties.load(is);
        }

        String format = properties.getProperty("quarkus.log.console.format");
        Assertions.assertNotNull(format, "quarkus.log.console.format should be defined");

        Matcher matcher = DATE_PATTERN.matcher(format);
        Assertions.assertTrue(matcher.find(), "Console log format should contain a %d{...} pattern");
        String datePattern = matcher.group(1);

        // Pattern must use calendar year (yyyy) rather than week-based year (YYYY)
        Assertions.assertFalse(datePattern.contains("YYYY"),
                "Date pattern must not use week-based year 'YYYY', should use calendar year 'yyyy': " + datePattern);
        Assertions.assertTrue(datePattern.contains("yyyy"),
                "Date pattern must contain calendar year 'yyyy': " + datePattern);

        // Pattern must not contain stray zone-offset letter 'x' in date segment
        Assertions.assertFalse(datePattern.contains("MMx"),
                "Date pattern must not contain stray zone offset 'x': " + datePattern);
        Assertions.assertEquals("yyyy-MM-dd HH:mm:ss", datePattern,
                "Expected standard date-time pattern");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern);

        // Verify late December date does not roll over to next year (defect 2)
        LocalDateTime endOfYear = LocalDateTime.of(2026, Month.DECEMBER, 31, 23, 59, 59);
        String formattedEndOfYear = formatter.format(endOfYear);
        Assertions.assertEquals("2026-12-31 23:59:59", formattedEndOfYear);

        // Verify standard date formatting (defect 1)
        LocalDateTime midYear = LocalDateTime.of(2026, Month.OCTOBER, 28, 18, 15, 58);
        String formattedMidYear = formatter.format(midYear);
        Assertions.assertEquals("2026-10-28 18:15:58", formattedMidYear);
    }
}
