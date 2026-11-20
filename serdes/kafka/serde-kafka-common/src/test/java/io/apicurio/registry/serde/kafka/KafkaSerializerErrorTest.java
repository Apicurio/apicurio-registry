package io.apicurio.registry.serde.kafka;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.serde.AbstractSerializer;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaSerializerErrorTest {

    private static final String MESSAGE = "Registry request failed (HTTP 409) due to COMPATIBILITY rule violation: "
            + "Incompatible artifact: orders-value [AVRO]";

    @Test
    void registryFailureIsThrownAsSerializationException() {
        RuleViolationProblemDetails problem = new RuleViolationProblemDetails();
        RuntimeException resolverFailure = new RuntimeException(MESSAGE, problem);
        KafkaSerializer<Object, String> serializer = new KafkaSerializer<>(failingSerializer(resolverFailure)) {
        };

        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.serialize("orders", "value"));
        assertEquals(MESSAGE, ex.getMessage());
        assertSame(resolverFailure, ex.getCause());

        SerializationException withHeaders = assertThrows(SerializationException.class,
                () -> serializer.serialize("orders", new RecordHeaders(), "value"));
        assertEquals(MESSAGE, withHeaders.getMessage());
    }

    @Test
    void nonRegistryFailureIsRethrownUnchanged() {
        IllegalStateException failure = new IllegalStateException("boom");
        KafkaSerializer<Object, String> serializer = new KafkaSerializer<>(failingSerializer(failure)) {
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> serializer.serialize("orders", "value"));
        assertSame(failure, ex);
    }

    private static AbstractSerializer<Object, String> failingSerializer(RuntimeException failure) {
        return new AbstractSerializer<>() {
            @Override
            public SchemaParser<Object, String> schemaParser() {
                return null;
            }

            @Override
            public void serializeData(ParsedSchema<Object> schema, String data, OutputStream out) {
                throw failure;
            }

            @Override
            public byte[] serializeData(String topic, String data) {
                throw failure;
            }
        };
    }
}
