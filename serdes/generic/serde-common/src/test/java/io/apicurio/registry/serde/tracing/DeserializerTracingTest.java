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
import io.apicurio.registry.serde.AbstractDeserializer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A payload whose schema comes from the record headers (no ID prefix) is traced like one with an ID
 * prefix, and a schema lookup that misses the cache gets its own span.
 */
class DeserializerTracingTest {

    private InMemorySpanExporter spanExporter;
    private final AtomicInteger lookups = new AtomicInteger();
    private final ArtifactReference reference = ArtifactReference.builder().globalId(42L).build();
    private StringDeserializer deserializer;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        spanExporter = InMemorySpanExporter.create();
        OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build())
                .buildAndRegisterGlobal();
        deserializer = new StringDeserializer(new StubResolver());
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void headerPathIsTracedAndOnlyACacheMissResolves() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        assertEquals("hello", deserializer.readData("orders", data, reference));
        List<SpanData> first = spanExporter.getFinishedSpanItems();
        assertEquals(List.of("serde.resolve_schema", "serde.deserialize"), first.stream().map(SpanData::getName).toList());
        SpanData resolve = first.get(0);
        SpanData deserialize = first.get(1);
        assertEquals(deserialize.getSpanContext().getSpanId(), resolve.getParentSpanId());
        assertEquals("deserialize", resolve.getAttributes().get(SerDesAttributes.OPERATION));
        assertEquals("orders", deserialize.getAttributes().get(SerDesAttributes.TOPIC));
        assertEquals(5L, deserialize.getAttributes().get(SerDesAttributes.DATA_SIZE));
        assertEquals(false, deserialize.getAttributes().get(SerDesAttributes.CACHE_HIT));
        assertEquals("OrderEvent", deserialize.getAttributes().get(SerDesAttributes.ARTIFACT_ID));

        spanExporter.reset();
        assertEquals("hello", deserializer.readData("orders", data, reference));
        List<SpanData> second = spanExporter.getFinishedSpanItems();
        assertEquals(List.of("serde.deserialize"), second.stream().map(SpanData::getName).toList());
        assertEquals(true, second.get(0).getAttributes().get(SerDesAttributes.CACHE_HIT));
        assertEquals(1, lookups.get());
    }

    private static final class StringDeserializer extends AbstractDeserializer<String, String> {
        StringDeserializer(SchemaResolver<String, String> resolver) {
            super(resolver);
        }

        @Override
        public SchemaParser<String, String> schemaParser() {
            return null;
        }

        @Override
        protected String readData(ParsedSchema<String> schema, ByteBuffer buffer, int start, int length) {
            return new String(buffer.array(), start, length, StandardCharsets.UTF_8);
        }
    }

    private final class StubResolver implements SchemaResolver<String, String> {
        @Override
        public SchemaLookupResult<String> resolveSchemaByArtifactReference(ArtifactReference ref) {
            lookups.incrementAndGet();
            return SchemaLookupResult.<String>builder().parsedSchema(new ParsedSchemaImpl<String>())
                    .globalId(42L).groupId("default").artifactId("OrderEvent").version("1").build();
        }

        @Override
        public SchemaLookupResult<String> resolveSchema(Record<String> data) {
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
