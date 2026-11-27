package io.apicurio.registry.serde.tracing;

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
import io.apicurio.registry.serde.data.SerdeMetadata;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializerTracingTest {

    private InMemorySpanExporter spanExporter;
    private final AtomicInteger lookups = new AtomicInteger();
    private StringSerializer serializer;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        spanExporter = InMemorySpanExporter.create();
        OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build())
                .buildAndRegisterGlobal();
        serializer = new StringSerializer(new StubResolver());
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void headerPathIsTracedAndOnlyACacheMissResolves() {
        byte[] expected = "hello".getBytes(StandardCharsets.UTF_8);

        byte[] result = serializer.serializeData("orders", new SerdeMetadata("orders", false), "hello",
                (schema, toWrite) -> toWrite.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, result);

        List<SpanData> first = spanExporter.getFinishedSpanItems();
        assertEquals(List.of("serde.resolve_schema", "serde.serialize"), first.stream().map(SpanData::getName).toList());
        SpanData resolve = first.get(0);
        SpanData serialize = first.get(1);
        assertEquals(serialize.getSpanContext().getSpanId(), resolve.getParentSpanId());
        assertEquals("serialize", resolve.getAttributes().get(SerDesAttributes.OPERATION));
        assertEquals("orders", serialize.getAttributes().get(SerDesAttributes.TOPIC));
        assertEquals(5L, serialize.getAttributes().get(SerDesAttributes.DATA_SIZE));
        assertFalse(serialize.getAttributes().get(SerDesAttributes.CACHE_HIT));
        assertEquals("OrderEvent", serialize.getAttributes().get(SerDesAttributes.ARTIFACT_ID));

        spanExporter.reset();
        result = serializer.serializeData("orders", new SerdeMetadata("orders", false), "hello",
                (schema, toWrite) -> toWrite.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, result);

        List<SpanData> second = spanExporter.getFinishedSpanItems();
        assertEquals(List.of("serde.serialize"), second.stream().map(SpanData::getName).toList());
        assertTrue(second.get(0).getAttributes().get(SerDesAttributes.CACHE_HIT));
        assertEquals(1, lookups.get());
    }

    @Test
    void explicitSchemaContentBypassesFastPathCache() {
        SerdeMetadata metadataWithExplicitSchema = new SerdeMetadata("orders", false) {
            @Override
            public String explicitSchemaContent() {
                return "{\"type\":\"string\"}";
            }
        };

        serializer.serializeData("orders", metadataWithExplicitSchema, "hello",
                (schema, toWrite) -> toWrite.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, lookups.get());

        serializer.serializeData("orders", metadataWithExplicitSchema, "hello",
                (schema, toWrite) -> toWrite.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, lookups.get());
    }

    private static final class StringSerializer extends AbstractSerializer<String, String> {
        StringSerializer(SchemaResolver<String, String> resolver) {
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

    private final class StubResolver implements SchemaResolver<String, String> {
        @Override
        public SchemaLookupResult<String> resolveSchema(Record<String> data) {
            lookups.incrementAndGet();
            return SchemaLookupResult.<String>builder().parsedSchema(new ParsedSchemaImpl<String>())
                    .globalId(42L).groupId("default").artifactId("OrderEvent").version("1").build();
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
