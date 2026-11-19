package io.apicurio.registry.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The packaged application.properties is filtered by Maven. Properties whose default is the temporary
 * directory must keep the expression, so it resolves where the registry runs, not on the build machine.
 */
public class TmpdirPropertiesFilteringTest {

    @ParameterizedTest
    @ValueSource(strings = { "apicurio.import.work-dir", "apicurio.storage.snapshot.location" })
    void tmpdirExpressionSurvivesResourceFiltering(String property) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties is not on the classpath");
            properties.load(in);
        }

        assertEquals("${java.io.tmpdir}", properties.getProperty(property));
    }
}
