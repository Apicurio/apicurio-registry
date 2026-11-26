package io.apicurio.registry.observability;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OtelConfigPropertiesDefaultsTest {

    @Test
    void declaredSamplerDefaultsMatchApplicationProperties() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            props.load(in);
        }

        assertEquals("parentbased_traceidratio", props.getProperty("quarkus.otel.traces.sampler"));
        assertEquals("0.1", props.getProperty("quarkus.otel.traces.sampler.arg"));
        assertEquals("parentbased_traceidratio", declaredDefault("otelTracesSampler"));
        assertEquals("0.1", declaredDefault("otelTracesSamplerArg"));
    }

    private static String declaredDefault(String field) throws NoSuchFieldException {
        return OtelConfigProperties.class.getDeclaredField(field).getAnnotation(ConfigProperty.class)
                .defaultValue();
    }
}
