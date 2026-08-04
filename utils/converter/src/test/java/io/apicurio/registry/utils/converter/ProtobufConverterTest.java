package io.apicurio.registry.utils.converter;

import org.apache.kafka.connect.storage.Converter;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufConverterTest {

    @Test
    public void testConverterIsRegisteredAsServiceProvider() {
        boolean found = false;
        for (Converter converter : ServiceLoader.load(Converter.class)) {
            if (converter instanceof ProtobufConverter) {
                found = true;
                break;
            }
        }

        assertTrue(found, "ProtobufConverter must be registered for Kafka Connect discovery");
    }
}
