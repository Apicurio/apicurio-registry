package io.apicurio.registry.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;

import java.io.InputStream;
import java.net.URL;

public class HealthUtils {

    public enum Type {
        READY, LIVE
    }

    public static void assertHealthCheck(int port, Type type, HealthResponse.Status status) throws Exception {
        URL url = new URL(String.format("http://localhost:%s/health/%s", port, type.name().toLowerCase()));
        try (InputStream stream = url.openStream()) {
            HealthResponse hr = new ObjectMapper().readValue(stream, HealthResponse.class);
            Assertions.assertEquals(status, hr.status);
        }
    }

    public static void assertIsReady(int port) throws Exception {
        assertHealthCheck(port, Type.READY, HealthResponse.Status.UP);
    }

    public static void assertIsLive(int port) throws Exception {
        assertHealthCheck(port, Type.LIVE, HealthResponse.Status.UP);
    }
}
