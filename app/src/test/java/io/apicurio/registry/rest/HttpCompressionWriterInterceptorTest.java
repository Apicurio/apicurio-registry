package io.apicurio.registry.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HttpCompressionWriterInterceptor#clientAcceptsGzip(String)}, verifying
 * correct parsing of the Accept-Encoding header per RFC 9110 §12.5.3.
 */
class HttpCompressionWriterInterceptorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "gzip",
            "gzip, deflate",
            "deflate, gzip",
            "gzip;q=1",
            "gzip;q=1.0",
            "gzip;q=0.5",
            "gzip ; q=0.001",
            "deflate, gzip;q=0.8",
            "GZIP",
            "Gzip",
            "gzip;q=1, deflate;q=0.5"
    })
    void clientAcceptsGzip_shouldReturnTrue(String header) {
        assertTrue(HttpCompressionWriterInterceptor.clientAcceptsGzip(header),
                "Expected true for Accept-Encoding: " + header);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "deflate",
            "identity",
            "gzip;q=0",
            "gzip;q=0.0",
            "gzip;q=0.000",
            "gzip ; q=0",
            "deflate, gzip;q=0",
            "br"
    })
    void clientAcceptsGzip_shouldReturnFalse(String header) {
        assertFalse(HttpCompressionWriterInterceptor.clientAcceptsGzip(header),
                "Expected false for Accept-Encoding: " + header);
    }

    @Test
    void clientAcceptsGzip_shouldNotMatchGzipPrefix() {
        // "gzipx" is not the same as "gzip"
        assertFalse(HttpCompressionWriterInterceptor.clientAcceptsGzip("gzipx"));
    }

    @Test
    void clientAcceptsGzip_malformedQValue_treatedAsAcceptance() {
        // A malformed q-value (non-numeric) should be treated as acceptance (fail-open)
        assertTrue(HttpCompressionWriterInterceptor.clientAcceptsGzip("gzip;q=abc"));
    }
}
