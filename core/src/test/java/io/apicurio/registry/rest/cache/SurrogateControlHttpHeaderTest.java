package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.cache.headers.SurrogateControlHttpHeader;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SurrogateControlHttpHeaderTest {

    @Test
    void immutableHeader() {
        var header = SurrogateControlHttpHeader.builder()
                .immutable(true)
                .expiration(Duration.ofDays(10))
                .build();
        assertEquals("Surrogate-Control", header.key());
        assertEquals("public, max-age=864000, immutable", header.value());
    }

    @Test
    void mutableHeader() {
        var header = SurrogateControlHttpHeader.builder()
                .immutable(false)
                .expiration(Duration.ofSeconds(30))
                .build();
        assertEquals("public, max-age=30, must-revalidate", header.value());
    }

    @Test
    void nullExpirationThrows() {
        var header = SurrogateControlHttpHeader.builder()
                .expiration(null)
                .build();
        assertThrows(NullPointerException.class, header::value);
    }
}
