package io.apicurio.registry.client.common.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.microsoft.kiota.serialization.ParseNode;

import io.kiota.serialization.json.JsonParseNode;
import io.kiota.serialization.json.JsonParseNodeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateTimeUtilTest {

    final JsonParseNodeFactory nodeFactory = new JsonParseNodeFactory();

    @Test
    void testNullInput() {
        var parsedValue = DateTimeUtil.getOffsetDateTimeValue(null);
        assertNull(parsedValue);
    }

    @Test
    void testNullInputNode() {
        ParseNode node = new JsonParseNode(nodeFactory, NullNode.instance);
        var parsedValue = DateTimeUtil.getOffsetDateTimeValue(node);
        assertNull(parsedValue);
    }

    @Test
    void testStandardFormat() {
        Instant now = Instant.now();
        ParseNode node = new JsonParseNode(nodeFactory, new TextNode(now.toString()));
        var parsedValue = DateTimeUtil.getOffsetDateTimeValue(node);
        assertEquals(now.atOffset(ZoneOffset.UTC), parsedValue);
    }

    @Test
    void testLegacyFormat() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern(DateTimeUtil.DEFAULT_LEGACY_FORMAT);
        TemporalAccessor now = OffsetDateTime.now()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .withNano(0);
        ParseNode node = new JsonParseNode(nodeFactory, new TextNode(format.format(now)));
        var parsedValue = DateTimeUtil.getOffsetDateTimeValue(node);
        assertEquals(now, parsedValue);
    }

    @Test
    void testLegacyFormatBadValue() {
        ParseNode node = new JsonParseNode(nodeFactory, new TextNode("Not A Valid Date"));
        assertThrows(DateTimeParseException.class, () -> DateTimeUtil.getOffsetDateTimeValue(node));
    }
}
