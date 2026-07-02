package io.apicurio.registry.services;

import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CorsConfigInterceptorTest {

    private CorsConfigInterceptor interceptor;
    private ConfigSourceInterceptorContext context;

    private static final String CORS_ORIGINS = "quarkus.http.cors.origins";
    private static final String URL_OVERRIDE_HOST = "apicurio.url.override.host";
    private static final String URL_OVERRIDE_PORT = "apicurio.url.override.port";
    private static final String BASE_ORIGINS = "http://localhost:8888,http://127.0.0.1:8888";

    @BeforeEach
    void setUp() {
        interceptor = new CorsConfigInterceptor();
        context = mock(ConfigSourceInterceptorContext.class);
    }

    @Test
    void testPassthroughForOtherProperties() {
        ConfigValue expected = configValue("some.other.property", "value");
        when(context.proceed("some.other.property")).thenReturn(expected);

        ConfigValue result = interceptor.getValue(context, "some.other.property");

        assertEquals(expected, result);
    }

    @Test
    void testNoUrlOverride() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(null);

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS, result.getValue());
    }

    @Test
    void testBlankHostOverride() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(configValue(URL_OVERRIDE_HOST, "  "));

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS, result.getValue());
    }

    @Test
    void testHostOnlyNoPort() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(configValue(URL_OVERRIDE_HOST, "apicurio.example.com"));
        when(context.proceed(URL_OVERRIDE_PORT)).thenReturn(null);

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS + ",http://apicurio.example.com,https://apicurio.example.com",
                result.getValue());
    }

    @Test
    void testHostWithCustomPort() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(configValue(URL_OVERRIDE_HOST, "apicurio.example.com"));
        when(context.proceed(URL_OVERRIDE_PORT)).thenReturn(configValue(URL_OVERRIDE_PORT, "9443"));

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS + ",http://apicurio.example.com:9443,https://apicurio.example.com:9443",
                result.getValue());
    }

    @Test
    void testHostWithPort443() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(configValue(URL_OVERRIDE_HOST, "apicurio.example.com"));
        when(context.proceed(URL_OVERRIDE_PORT)).thenReturn(configValue(URL_OVERRIDE_PORT, "443"));

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS + ",http://apicurio.example.com,https://apicurio.example.com",
                result.getValue());
    }

    @Test
    void testHostWithPort80() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(configValue(URL_OVERRIDE_HOST, "apicurio.example.com"));
        when(context.proceed(URL_OVERRIDE_PORT)).thenReturn(configValue(URL_OVERRIDE_PORT, "80"));

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS + ",http://apicurio.example.com,https://apicurio.example.com",
                result.getValue());
    }

    @Test
    void testNullBaseValue() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(null);
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(configValue(URL_OVERRIDE_HOST, "apicurio.example.com"));
        when(context.proceed(URL_OVERRIDE_PORT)).thenReturn(null);

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals("http://apicurio.example.com,https://apicurio.example.com", result.getValue());
    }

    @Test
    void testInvalidPort() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(configValue(URL_OVERRIDE_HOST, "apicurio.example.com"));
        when(context.proceed(URL_OVERRIDE_PORT)).thenReturn(configValue(URL_OVERRIDE_PORT, "notanumber"));

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS + ",http://apicurio.example.com,https://apicurio.example.com",
                result.getValue());
    }

    @Test
    void testNullHostValue() {
        when(context.proceed(CORS_ORIGINS)).thenReturn(configValue(CORS_ORIGINS, BASE_ORIGINS));
        ConfigValue nullValueHost = ConfigValue.builder().withName(URL_OVERRIDE_HOST).withValue(null).build();
        when(context.proceed(URL_OVERRIDE_HOST)).thenReturn(nullValueHost);

        ConfigValue result = interceptor.getValue(context, CORS_ORIGINS);

        assertNotNull(result);
        assertEquals(BASE_ORIGINS, result.getValue());
    }

    private static ConfigValue configValue(String name, String value) {
        return ConfigValue.builder().withName(name).withValue(value).build();
    }
}
