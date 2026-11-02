package io.apicurio.registry.utils.converter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerdeBasedConverterTest {

    @Test
    void configureWithoutSerializerThrows() {
        SerdeBasedConverter<Object, Object> converter = new SerdeBasedConverter<>();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> converter.configure(Map.of(), false));

        assertEquals("Missing required configuration: "
                + SerdeBasedConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, ex.getMessage());
    }

    @Test
    void configureWithoutDeserializerThrows() {
        SerdeBasedConverter<Object, Object> converter = new SerdeBasedConverter<>();
        Map<String, Object> configs = Map.of(SerdeBasedConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM,
                "org.apache.kafka.common.serialization.ByteArraySerializer");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> converter.configure(configs, false));

        assertEquals("Missing required configuration: "
                + SerdeBasedConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, ex.getMessage());
    }
}
