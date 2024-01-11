package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.generic.GenericDeserializer;
import io.apicurio.registry.serde.generic.GenericSerDeConfig;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

import static io.apicurio.registry.serde.SerdeConfig.IS_KEY;

public class AvroKafkaDeserializer2<DATA> implements Deserializer<DATA> {


    private GenericDeserializer<Schema, DATA> genericDeserializer;


    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        var config = new GenericSerDeConfig((Map<String, Object>) configs);
        config.setConfig(IS_KEY, isKey);

        var datatype = new GenericAvroSerDeDatatype<DATA>();
        datatype.configure(config);

        genericDeserializer = new GenericDeserializer<>(datatype, null);
        genericDeserializer.configure(config);
    }


    @Override
    public DATA deserialize(String topic, byte[] data) {
        return genericDeserializer.deserialize(topic, null, data);
    }


    @Override
    public DATA deserialize(String topic, Headers headers, byte[] data) {
        return genericDeserializer.deserialize(topic, headers, data);
    }


    @Override
    public void close() {
        genericDeserializer.close();
    }
}
