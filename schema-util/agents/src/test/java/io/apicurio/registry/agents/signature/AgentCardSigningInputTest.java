package io.apicurio.registry.agents.signature;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentCardSigningInputTest {

    /** The "Example of Default Value Removal" of A2A v1.0, section 8.4.1, with a signature added. */
    private static final String SPEC_EXAMPLE = "{\"name\":\"Example Agent\","
            + "\"description\":\"An example agent that echoes messages.\","
            + "\"supportedInterfaces\":[{\"url\":\"https://example.com/a2a/v1\",\"protocolBinding\":\"JSONRPC\","
            + "\"protocolVersion\":\"1.0\"}],\"version\":\"1.0.0\","
            + "\"capabilities\":{\"streaming\":false,\"pushNotifications\":false,\"extensions\":[]},"
            + "\"defaultInputModes\":[\"text/plain\"],\"defaultOutputModes\":[\"text/plain\"],"
            + "\"skills\":[{\"id\":\"echo\",\"name\":\"Echo\",\"description\":\"Echoes the input.\","
            + "\"tags\":[\"echo\"]}],"
            + "\"signatures\":[{\"protected\":\"e30\",\"signature\":\"AA\"}]}";

    /** The canonical form section 8.4.1 gives for the example. */
    private static final String SPEC_CANONICAL = "{\"capabilities\":{\"pushNotifications\":false,\"streaming\":false},"
            + "\"defaultInputModes\":[\"text/plain\"],\"defaultOutputModes\":[\"text/plain\"],"
            + "\"description\":\"An example agent that echoes messages.\",\"name\":\"Example Agent\","
            + "\"skills\":[{\"description\":\"Echoes the input.\",\"id\":\"echo\",\"name\":\"Echo\",\"tags\":[\"echo\"]}],"
            + "\"supportedInterfaces\":[{\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\","
            + "\"url\":\"https://example.com/a2a/v1\"}],\"version\":\"1.0.0\"}";

    @Test
    void reproducesTheSpecificationExampleWithoutSignaturesAndEmptyValues() {
        List<byte[]> payloads = AgentCardSigningInput.payloads(JsonCanonicalizer.parseStrict(SPEC_EXAMPLE));

        assertEquals(2, payloads.size());
        assertEquals(SPEC_CANONICAL.replace("\"capabilities\":{", "\"capabilities\":{\"extensions\":[],"),
                new String(payloads.get(0), StandardCharsets.UTF_8));
        assertEquals(SPEC_CANONICAL, new String(payloads.get(1), StandardCharsets.UTF_8));
    }

    @Test
    void cardWithoutEmptyValuesHasOnePayload() {
        List<byte[]> payloads = AgentCardSigningInput.payloads(
                JsonCanonicalizer.parseStrict("{\"name\":\"a\",\"signatures\":[]}"));

        assertEquals(1, payloads.size());
        assertEquals("{\"name\":\"a\"}", new String(payloads.get(0), StandardCharsets.UTF_8));
    }

    @Test
    void removesNullsAndNestedEmptyValues() {
        List<byte[]> payloads = AgentCardSigningInput.payloads(JsonCanonicalizer.parseStrict(
                "{\"a\":null,\"b\":\"\",\"c\":{\"d\":[{}, \"\", \"x\"]},\"e\":0,\"f\":false}"));

        assertEquals("{\"c\":{\"d\":[\"x\"]},\"e\":0,\"f\":false}", new String(payloads.get(1), StandardCharsets.UTF_8));
    }

    @Test
    void refusesANonObjectCard() {
        assertThrows(JsonCanonicalizer.CanonicalizationException.class,
                () -> AgentCardSigningInput.payloads(JsonCanonicalizer.parseStrict("[1]")));
    }
}
