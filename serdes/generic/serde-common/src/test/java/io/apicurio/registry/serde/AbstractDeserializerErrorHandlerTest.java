package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.error.DeserializerErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that a configured {@link DeserializerErrorHandler} can skip a record whose artifact
 * reference cannot be resolved, and that the default (unconfigured) behavior is unchanged: throw.
 * This is the mechanism behind the fix for the "endless rebalance loop" in
 * https://github.com/Apicurio/apicurio-registry/issues/3662.
 */
public class AbstractDeserializerErrorHandlerTest {

    private static final class ThrowingSchemaResolver implements SchemaResolver<String, String> {
        @Override
        public void setClientFacade(RegistryClientFacade clientFacade) {
        }

        @Override
        public void setArtifactResolverStrategy(
                ArtifactReferenceResolverStrategy<String, String> artifactResolverStrategy) {
        }

        @Override
        public SchemaParser<String, String> getSchemaParser() {
            return null;
        }

        @Override
        public SchemaLookupResult<String> resolveSchema(Record<String> data) {
            throw new IllegalStateException("should not be called in this test");
        }

        @Override
        public SchemaLookupResult<String> resolveSchemaByArtifactReference(ArtifactReference reference) {
            throw new IllegalStateException("artifact reference cannot be null");
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }
    }

    private static final class TestDeserializer extends AbstractDeserializer<String, String> {
        TestDeserializer(SchemaResolver<String, String> resolver) {
            super(resolver);
            getSerdeConfigurer().setIdHandler(new Default4ByteIdHandler());
        }

        @Override
        public SchemaParser<String, String> schemaParser() {
            return null;
        }

        @Override
        protected String readData(ParsedSchema<String> schema, ByteBuffer buffer, int start, int length) {
            return "decoded";
        }
    }

    private TestDeserializer deserializer;

    @BeforeEach
    void setup() {
        deserializer = new TestDeserializer(new ThrowingSchemaResolver());
    }

    private byte[] recordWithMagicByteAndId(int id) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put(BaseSerde.MAGIC_BYTE);
        buffer.putInt(id);
        return buffer.array();
    }

    @Test
    void byDefaultUnresolvableRecordThrows() {
        byte[] data = recordWithMagicByteAndId(1);
        assertThrows(IllegalStateException.class, () -> deserializer.deserializeData("topic", data));
    }

    @Test
    void configuredHandlerCanSkipUnresolvableRecord() {
        deserializer.setDeserializerErrorHandler((topic, data, cause) -> true);

        byte[] data = recordWithMagicByteAndId(1);
        assertNull(deserializer.deserializeData("topic", data));
    }

    @Test
    void handlerReturningFalseStillThrows() {
        deserializer.setDeserializerErrorHandler((topic, data, cause) -> false);

        byte[] data = recordWithMagicByteAndId(1);
        assertThrows(IllegalStateException.class, () -> deserializer.deserializeData("topic", data));
    }
}
