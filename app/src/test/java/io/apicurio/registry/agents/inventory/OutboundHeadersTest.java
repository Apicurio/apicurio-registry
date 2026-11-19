package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.agents.OutboundHeaders;
import io.apicurio.registry.rest.v3.beans.Headers;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboundHeadersTest {

    private static Headers headers(String name, String value) {
        Headers headers = new Headers();
        headers.setAdditionalProperty(name, value);
        return headers;
    }

    @Test
    void validHeadersAreKept() {
        assertEquals(Map.of("Authorization", "Bearer t"),
                OutboundHeaders.validate(headers("Authorization", "Bearer t"), Set.of("host"), "the source"));
        assertEquals(Map.of(), OutboundHeaders.validate(null, Set.of("host"), "the source"));
    }

    @Test
    void reservedNameIsRefusedWhateverItsCase() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> OutboundHeaders.validate(headers("HoSt", "x"), Set.of("host"), "the source"));
        assertEquals("Header 'HoSt' cannot be sent to the source.", e.getMessage());
    }

    @Test
    void valueWithALineBreakIsRefusedWithoutRepeatingIt() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> OutboundHeaders.validate(headers("X-Token", "secret\r\nX-Evil: 1"), Set.of(), "the source"));
        assertEquals("Header 'X-Token' has an invalid value.", e.getMessage());
        assertFalse(e.getMessage().contains("secret"));
    }
}
