package io.apicurio.registry.serde.headers;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.KafkaSerdeConfig;
import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
