package io.apicurio.registry.serde.kafka.headers;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultHeadersHandlerTest {

    @Test
    void testReadKeyHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "some key header name";
        String contentHashValue = "context hash value";
        Map<String, Object> configs = Collections
                .singletonMap(KafkaSerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(contentHashHeaderName, IoUtil.toBytes(contentHashValue)) });
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);

        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(contentHashValue, artifact.getContentHash());
    }

    @Test
    void testReadKeyHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another key header name";
        Map<String, Object> configs = Collections
                .singletonMap(KafkaSerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {});
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);

        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(null, artifact.getContentHash());
    }

    @Test
    void testReadValueHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "value header name";
        String contentHashValue = "some value";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(contentHashHeaderName, IoUtil.toBytes(contentHashValue)) });
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(contentHashValue, artifact.getContentHash());
    }

    @Test
    void testReadValueHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another value header name";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {});
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        ArtifactReference artifact = handler.readHeaders(headers);

        assertEquals(null, artifact.getContentHash());
    }

    @Test
    void testWriteKeyHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "write key header name";
        String contentHashValue = "some write key value";
        Map<String, Object> configs = Collections
                .singletonMap(KafkaSerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);
        ArtifactReference artifact = ArtifactReference.builder().contentHash(contentHashValue).build();

        handler.writeHeaders(headers, artifact);

        assertEquals(contentHashValue, IoUtil.toString(headers.lastHeader(contentHashHeaderName).value()));
    }

    @Test
    void testWriteKeyHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another header name";
        Map<String, Object> configs = Collections
                .singletonMap(KafkaSerdeConfig.HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);
        ArtifactReference artifact = ArtifactReference.builder().build();

        handler.writeHeaders(headers, artifact);

        assertEquals(null, headers.lastHeader(contentHashHeaderName));
    }

    @Test
    void testWriteValueHeadersHandlesPresentContentHash() {
        String contentHashHeaderName = "write value header name";
        String contentHashValue = "some write value";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        ArtifactReference artifact = ArtifactReference.builder().contentHash(contentHashValue).build();

        handler.writeHeaders(headers, artifact);

        assertEquals(contentHashValue, IoUtil.toString(headers.lastHeader(contentHashHeaderName).value()));
    }

    @Test
    void testWriteValueHeadersHandlesMissingContentHash() {
        String contentHashHeaderName = "another write key header name";
        Map<String, Object> configs = Map.of(KafkaSerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME,
                contentHashHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        ArtifactReference artifact = ArtifactReference.builder().build();

        handler.writeHeaders(headers, artifact);

        assertEquals(null, headers.lastHeader(contentHashHeaderName));
    }

    @Test
    void testWritesArtifactCoordinatesWhenContentHashPresent() {
        String contentHashHeaderName = "content hash";
        String artifactIdHeaderName = "artifact ID";
        String contentHashValue = "a content hash";
        String artifactIdValue = "a artifact ID";

        Map<String, Object> configs = new HashMap<>();
        configs.put(KafkaSerdeConfig.HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, contentHashHeaderName);
        configs.put(KafkaSerdeConfig.HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME, artifactIdHeaderName);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);
        ArtifactReference artifact = ArtifactReference.builder().contentHash(contentHashValue)
                .artifactId(artifactIdValue).build();

        handler.writeHeaders(headers, artifact);

        assertNotNull(headers.lastHeader(contentHashHeaderName));
        assertEquals(contentHashValue, IoUtil.toString(headers.lastHeader(contentHashHeaderName).value()));
        assertNotNull(headers.lastHeader(artifactIdHeaderName));
        assertEquals(artifactIdValue, IoUtil.toString(headers.lastHeader(artifactIdHeaderName).value()));
    }

    @Test
    void testUseSchemaFromHeadersDisabledByDefault() {
        Map<String, Object> configs = Collections.emptyMap();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        assertFalse(handler.isUseSchemaFromHeaders());
    }

    @Test
    void testUseSchemaFromHeadersEnabled() {
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        assertTrue(handler.isUseSchemaFromHeaders());
    }

    @Test
    void testReadSchemaFromValueHeaders() {
        String schemaContent = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": []}";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA, IoUtil.toBytes(schemaContent)) });
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        String result = handler.readSchemaFromHeaders(headers);

        assertEquals(schemaContent, result);
    }

    @Test
    void testReadSchemaFromKeyHeaders() {
        String schemaContent = "{\"type\": \"string\"}";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(KafkaSerdeHeaders.HEADER_KEY_SCHEMA, IoUtil.toBytes(schemaContent)) });
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);

        String result = handler.readSchemaFromHeaders(headers);

        assertEquals(schemaContent, result);
    }

    @Test
    void testReadSchemaFromHeadersReturnNullWhenMissing() {
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        String result = handler.readSchemaFromHeaders(headers);

        assertNull(result);
    }

    @Test
    void testReadSchemaTypeFromValueHeaders() {
        String schemaType = "AVRO";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA_TYPE, IoUtil.toBytes(schemaType)) });
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        String result = handler.readSchemaTypeFromHeaders(headers);

        assertEquals(schemaType, result);
    }

    @Test
    void testReadSchemaTypeFromKeyHeaders() {
        String schemaType = "PROTOBUF";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(KafkaSerdeHeaders.HEADER_KEY_SCHEMA_TYPE, IoUtil.toBytes(schemaType)) });
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, true);

        String result = handler.readSchemaTypeFromHeaders(headers);

        assertEquals(schemaType, result);
    }

    @Test
    void testReadSchemaTypeFromHeadersReturnNullWhenMissing() {
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        String result = handler.readSchemaTypeFromHeaders(headers);

        assertNull(result);
    }

    @Test
    void testWriteSchemaToHeaders() {
        String schemaContent = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": []}";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        handler.writeSchemaToHeaders(headers, schemaContent);

        assertNotNull(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA));
        assertEquals(schemaContent, IoUtil.toString(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA).value()));
    }

    @Test
    void testWriteSchemaToHeadersDoesNotWriteNull() {
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        handler.writeSchemaToHeaders(headers, null);

        assertNull(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA));
    }

    @Test
    void testWriteSchemaTypeToHeaders() {
        String schemaType = "AVRO";
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        handler.writeSchemaTypeToHeaders(headers, schemaType);

        assertNotNull(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA_TYPE));
        assertEquals(schemaType, IoUtil.toString(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA_TYPE).value()));
    }

    @Test
    void testWriteSchemaTypeToHeadersDoesNotWriteNull() {
        Map<String, Object> configs = Collections.singletonMap(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        RecordHeaders headers = new RecordHeaders();
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        handler.writeSchemaTypeToHeaders(headers, null);

        assertNull(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA_TYPE));
    }

    @Test
    void testCustomSchemaHeaderNames() {
        String customSchemaHeader = "my.custom.schema";
        String customSchemaTypeHeader = "my.custom.schemaType";
        String schemaContent = "{\"type\": \"string\"}";
        String schemaType = "JSON";

        Map<String, Object> configs = new HashMap<>();
        configs.put(KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true);
        configs.put(KafkaSerdeConfig.HEADER_VALUE_SCHEMA_OVERRIDE_NAME, customSchemaHeader);
        configs.put(KafkaSerdeConfig.HEADER_VALUE_SCHEMA_TYPE_OVERRIDE_NAME, customSchemaTypeHeader);

        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(customSchemaHeader, IoUtil.toBytes(schemaContent)),
                new RecordHeader(customSchemaTypeHeader, IoUtil.toBytes(schemaType)) });
        DefaultHeadersHandler handler = new DefaultHeadersHandler();
        handler.configure(configs, false);

        assertEquals(schemaContent, handler.readSchemaFromHeaders(headers));
        assertEquals(schemaType, handler.readSchemaTypeFromHeaders(headers));
    }
}
