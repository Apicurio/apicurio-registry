package io.apicurio.registry.serde.kafka;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.config.IdOption;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.kafka.data.KafkaSerdeMetadata;
import io.apicurio.registry.serde.kafka.headers.DefaultHeadersHandler;
import io.apicurio.registry.serde.kafka.headers.KafkaSerdeHeaders;
import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class KafkaSerializerHeadersTest {

    private TestResolver resolver;
    private TestSerializer delegatedSerializer;
    private KafkaSerializer<String, String> kafkaSerializer;
    private DefaultHeadersHandler defaultHeadersHandler;

    @BeforeEach
    void setUp() {
        resolver = new TestResolver();
        delegatedSerializer = new TestSerializer(resolver);
        kafkaSerializer = new KafkaSerializer<>(delegatedSerializer);
        kafkaSerializer.as4ByteId();
        defaultHeadersHandler = new DefaultHeadersHandler();
        defaultHeadersHandler.configure(Map.of(SerdeConfig.USE_ID, IdOption.globalId.name()), false);
        kafkaSerializer.setHeadersHandler(defaultHeadersHandler);
    }

    @Test
    void headersWrittenOnBothCacheMissAndCacheHit() {
        RecordHeaders headers1 = new RecordHeaders();
        byte[] payload1 = kafkaSerializer.serialize("orders", headers1, "hello");
        assertEquals("hello", new String(payload1, StandardCharsets.UTF_8));

        ArtifactReference ref1 = defaultHeadersHandler.readHeaders(headers1);
        assertEquals(42L, ref1.getGlobalId());
        assertEquals(1, resolver.lookups.get());

        RecordHeaders headers2 = new RecordHeaders();
        byte[] payload2 = kafkaSerializer.serialize("orders", headers2, "hello");
        assertEquals("hello", new String(payload2, StandardCharsets.UTF_8));

        ArtifactReference ref2 = defaultHeadersHandler.readHeaders(headers2);
        assertEquals(42L, ref2.getGlobalId());
        assertEquals(1, resolver.lookups.get());
    }

    @Test
    void useSchemaFromHeadersPassesExplicitSchemaToResolver() {
        DefaultHeadersHandler customHandler = new DefaultHeadersHandler();
        customHandler.configure(Map.of(
                KafkaSerdeConfig.USE_SCHEMA_FROM_HEADERS, true,
                SerdeConfig.USE_ID, IdOption.globalId.name()), false);
        kafkaSerializer.setHeadersHandler(customHandler);

        String schemaContent = "{\"type\":\"string\"}";
        String schemaType = "JSON";
        RecordHeaders headers1 = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA, IoUtil.toBytes(schemaContent)),
                new RecordHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA_TYPE, IoUtil.toBytes(schemaType))
        });

        byte[] payload1 = kafkaSerializer.serialize("orders", headers1, "hello");
        assertEquals("hello", new String(payload1, StandardCharsets.UTF_8));

        assertNotNull(resolver.lastRecord);
        assertNotNull(resolver.lastRecord.metadata());
        assertEquals(schemaContent, resolver.lastRecord.metadata().explicitSchemaContent());
        assertEquals(schemaType, resolver.lastRecord.metadata().explicitSchemaType());
        assertEquals(1, resolver.lookups.get());

        RecordHeaders headers2 = new RecordHeaders(new RecordHeader[] {
                new RecordHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA, IoUtil.toBytes(schemaContent)),
                new RecordHeader(KafkaSerdeHeaders.HEADER_VALUE_SCHEMA_TYPE, IoUtil.toBytes(schemaType))
        });
        byte[] payload2 = kafkaSerializer.serialize("orders", headers2, "hello");
        assertEquals("hello", new String(payload2, StandardCharsets.UTF_8));
        assertEquals(2, resolver.lookups.get());
    }

    @Test
    void distinctHeadersResolveDistinctArtifactsOnSameTopic() {
        RecordHeaders headers1 = new RecordHeaders(new RecordHeader[] {
                new RecordHeader("X-Artifact-Id", IoUtil.toBytes("OrderV1"))
        });
        byte[] payload1 = kafkaSerializer.serialize("orders", headers1, "hello");
        assertEquals("hello", new String(payload1, StandardCharsets.UTF_8));
        ArtifactReference ref1 = defaultHeadersHandler.readHeaders(headers1);
        assertEquals(42L, ref1.getGlobalId());

        RecordHeaders headers2 = new RecordHeaders(new RecordHeader[] {
                new RecordHeader("X-Artifact-Id", IoUtil.toBytes("OrderV2"))
        });
        byte[] payload2 = kafkaSerializer.serialize("orders", headers2, "hello");
        assertEquals("hello", new String(payload2, StandardCharsets.UTF_8));
        ArtifactReference ref2 = defaultHeadersHandler.readHeaders(headers2);
        assertEquals(43L, ref2.getGlobalId());

        assertEquals(2, resolver.lookups.get());
    }

    @Test
    void headersNullFallbackCallsDelegatedSerializer() {
        byte[] payload = kafkaSerializer.serialize("orders", (Headers) null, "hello");
        assertNotNull(payload);
        assertEquals(1, resolver.lookups.get());
    }

    @Test
    void dataNullReturnsNull() {
        assertNull(kafkaSerializer.serialize("orders", new RecordHeaders(), null));
        assertNull(kafkaSerializer.serialize("orders", null, null));
    }

    private static final class TestSerializer extends AbstractSerializer<String, String> {
        TestSerializer(SchemaResolver<String, String> resolver) {
            super(resolver);
        }

        @Override
        public SchemaParser<String, String> schemaParser() {
            return null;
        }

        @Override
        protected Object getSchemaCacheKey(String data) {
            return data.getClass();
        }

        @Override
        public void serializeData(ParsedSchema<String> schema, String data, OutputStream out) throws IOException {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class TestResolver implements SchemaResolver<String, String> {
        private final AtomicInteger lookups = new AtomicInteger();
        private volatile Record<String> lastRecord;

        @Override
        public SchemaLookupResult<String> resolveSchema(Record<String> data) {
            lookups.incrementAndGet();
            this.lastRecord = data;

            long globalId = 42L;
            String artifactId = "OrderEvent";
            if (data.metadata() instanceof KafkaSerdeMetadata kmd && kmd.getHeaders() != null) {
                Header h = kmd.getHeaders().lastHeader("X-Artifact-Id");
                if (h != null) {
                    String value = IoUtil.toString(h.value());
                    if ("OrderV1".equals(value)) {
                        globalId = 42L;
                        artifactId = "OrderV1";
                    } else if ("OrderV2".equals(value)) {
                        globalId = 43L;
                        artifactId = "OrderV2";
                    }
                }
            }

            return SchemaLookupResult.<String>builder()
                    .parsedSchema(new ParsedSchemaImpl<String>())
                    .globalId(globalId)
                    .groupId("default")
                    .artifactId(artifactId)
                    .version("1")
                    .build();
        }

        @Override
        public SchemaLookupResult<String> resolveSchemaByArtifactReference(ArtifactReference ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setClientFacade(RegistryClientFacade clientFacade) {
        }

        @Override
        public void setArtifactResolverStrategy(ArtifactReferenceResolverStrategy<String, String> strategy) {
        }

        @Override
        public SchemaParser<String, String> getSchemaParser() {
            return null;
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }
    }
}
