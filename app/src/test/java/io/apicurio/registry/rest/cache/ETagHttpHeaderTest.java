package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.cache.headers.ETagHttpHeader;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ETagHttpHeaderTest {

    private static RuntimeDelegate previousDelegate;

    @BeforeAll
    static void initJaxRs() {
        previousDelegate = RuntimeDelegate.getInstance();
        RuntimeDelegate.setInstance(new ResteasyProviderFactoryImpl());
    }

    @AfterAll
    static void restoreJaxRs() {
        RuntimeDelegate.setInstance(previousDelegate);
    }

    private ETagHttpHeader header(String value) {
        return ETagHttpHeader.builder()
                .etag(new EntityTag(value))
                .build();
    }

    @Test
    void headerKeyAndValue() {
        var h = header("abc123");
        assertEquals("ETag", h.key());
        assertEquals("\"abc123\"", h.value());
    }

    @Test
    void matchesExactEtag() {
        assertTrue(header("abc123").matches("\"abc123\""));
    }

    @Test
    void doesNotMatchDifferentEtag() {
        assertFalse(header("abc123").matches("\"xyz789\""));
    }

    @Test
    void matchesWildcard() {
        assertTrue(header("abc123").matches("*"));
    }

    @Test
    void matchesWildcardWithWhitespace() {
        assertTrue(header("abc123").matches("  *  "));
    }

    @Test
    void matchesWeakEtag() {
        // RFC 7232: weak comparison ignores W/ prefix
        assertTrue(header("abc123").matches("W/\"abc123\""));
    }

    @Test
    void matchesInCommaList() {
        assertTrue(header("abc123").matches("\"xyz\", \"abc123\", \"def\""));
    }

    @Test
    void doesNotMatchEmptyList() {
        assertFalse(header("abc123").matches(""));
    }

    @Test
    void doesNotMatchNull() {
        assertFalse(header("abc123").matches(null));
    }

    @Test
    void matchesComplexEtagValue() {
        // ETag values from ETagBuilder contain key=value;key=value
        var h = header("contentId=42;references=DEREFERENCE");
        assertTrue(h.matches("\"contentId=42;references=DEREFERENCE\""));
        assertFalse(h.matches("\"contentId=42\""));
    }
}
