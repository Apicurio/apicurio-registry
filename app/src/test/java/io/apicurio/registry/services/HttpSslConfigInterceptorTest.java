package io.apicurio.registry.services;

import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpSslConfigInterceptor}.
 */
public class HttpSslConfigInterceptorTest {

    private HttpSslConfigInterceptor interceptor;
    private ConfigSourceInterceptorContext context;

    @BeforeEach
    void setUp() {
        interceptor = new HttpSslConfigInterceptor();
        context = mock(ConfigSourceInterceptorContext.class);
    }

    @Test
    void testProtocolsMappedWhenApicurioPropertySet() {
        when(context.proceed("quarkus.http.ssl.protocols")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.protocols"))
                .thenReturn(configValue("apicurio.http.ssl.protocols", "TLSv1.3", "testSource"));

        ConfigValue result = interceptor.getValue(context, "quarkus.http.ssl.protocols");
        assertEquals("TLSv1.3", result.getValue());
        assertEquals("testSource", result.getConfigSourceName());
    }

    @Test
    void testCipherSuitesMappedWhenApicurioPropertySet() {
        when(context.proceed("quarkus.http.ssl.cipher-suites")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.cipher-suites"))
                .thenReturn(configValue("apicurio.http.ssl.cipher-suites", "TLS_AES_256_GCM_SHA384", "testSource"));

        ConfigValue result = interceptor.getValue(context, "quarkus.http.ssl.cipher-suites");
        assertEquals("TLS_AES_256_GCM_SHA384", result.getValue());
        assertEquals("testSource", result.getConfigSourceName());
    }

    @Test
    void testQuarkusDefaultPreservedWhenNoApicurioProperty() {
        when(context.proceed("quarkus.http.ssl.protocols")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.protocols")).thenReturn(null);

        ConfigValue result = interceptor.getValue(context, "quarkus.http.ssl.protocols");
        assertNull(result);
    }

    @Test
    void testExplicitQuarkusValueRespected() {
        when(context.proceed("quarkus.http.ssl.protocols"))
                .thenReturn(configValue("quarkus.http.ssl.protocols", "TLSv1.2", "quarkusSource"));

        ConfigValue result = interceptor.getValue(context, "quarkus.http.ssl.protocols");
        assertEquals("TLSv1.2", result.getValue());
        assertEquals("quarkusSource", result.getConfigSourceName());
    }

    @Test
    void testUnrelatedPropertyPassesThrough() {
        ConfigValue original = configValue("some.other.property", "value", "someSource");
        when(context.proceed("some.other.property")).thenReturn(original);

        ConfigValue result = interceptor.getValue(context, "some.other.property");
        assertEquals("value", result.getValue());
        assertEquals("someSource", result.getConfigSourceName());
    }

    @Test
    void testBlankApicurioValueIgnored() {
        when(context.proceed("quarkus.http.ssl.protocols")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.protocols"))
                .thenReturn(configValue("apicurio.http.ssl.protocols", "   ", "testSource"));

        ConfigValue result = interceptor.getValue(context, "quarkus.http.ssl.protocols");
        assertNull(result);
    }

    @Test
    void testBothPropertiesMappedIndependently() {
        when(context.proceed("quarkus.http.ssl.protocols")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.protocols"))
                .thenReturn(configValue("apicurio.http.ssl.protocols", "TLSv1.3", "protocolsSource"));

        ConfigValue protocolsResult = interceptor.getValue(context, "quarkus.http.ssl.protocols");
        assertEquals("TLSv1.3", protocolsResult.getValue());
        assertEquals("protocolsSource", protocolsResult.getConfigSourceName());

        when(context.proceed("quarkus.http.ssl.cipher-suites")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.cipher-suites"))
                .thenReturn(configValue("apicurio.http.ssl.cipher-suites", "TLS_AES_256_GCM_SHA384", "ciphersSource"));

        ConfigValue ciphersResult = interceptor.getValue(context, "quarkus.http.ssl.cipher-suites");
        assertEquals("TLS_AES_256_GCM_SHA384", ciphersResult.getValue());
        assertEquals("ciphersSource", ciphersResult.getConfigSourceName());
    }

    @Test
    void testApicurioOverridesQuarkusDefault() {
        // Quarkus has a default value from DefaultValuesConfigSource
        when(context.proceed("quarkus.http.ssl.protocols"))
                .thenReturn(configValue("quarkus.http.ssl.protocols", "TLSv1.3,TLSv1.2", "DefaultValuesConfigSource"));
        when(context.proceed("apicurio.http.ssl.protocols"))
                .thenReturn(configValue("apicurio.http.ssl.protocols", "TLSv1.3", "SysPropConfigSource"));

        ConfigValue result = interceptor.getValue(context, "quarkus.http.ssl.protocols");
        assertEquals("TLSv1.3", result.getValue());
        assertEquals("SysPropConfigSource", result.getConfigSourceName());
    }

    @Test
    void testManagementProtocolsMappedWhenApicurioPropertySet() {
        when(context.proceed("quarkus.management.ssl.protocols")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.protocols"))
                .thenReturn(configValue("apicurio.http.ssl.protocols", "TLSv1.3", "testSource"));

        ConfigValue result = interceptor.getValue(context, "quarkus.management.ssl.protocols");
        assertEquals("TLSv1.3", result.getValue());
        assertEquals("quarkus.management.ssl.protocols", result.getName());
    }

    @Test
    void testManagementCipherSuitesMappedWhenApicurioPropertySet() {
        when(context.proceed("quarkus.management.ssl.cipher-suites")).thenReturn(null);
        when(context.proceed("apicurio.http.ssl.cipher-suites"))
                .thenReturn(configValue("apicurio.http.ssl.cipher-suites", "TLS_AES_256_GCM_SHA384", "testSource"));

        ConfigValue result = interceptor.getValue(context, "quarkus.management.ssl.cipher-suites");
        assertEquals("TLS_AES_256_GCM_SHA384", result.getValue());
        assertEquals("quarkus.management.ssl.cipher-suites", result.getName());
    }

    private ConfigValue configValue(String name, String value, String sourceName) {
        int ordinal = "DefaultValuesConfigSource".equals(sourceName) ? Integer.MIN_VALUE : 100;
        return ConfigValue.builder()
                .withName(name)
                .withValue(value)
                .withConfigSourceName(sourceName)
                .withConfigSourceOrdinal(ordinal)
                .build();
    }
}

