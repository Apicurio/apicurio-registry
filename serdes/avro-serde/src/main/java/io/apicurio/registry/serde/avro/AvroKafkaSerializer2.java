package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.generic.GenericSerDeConfig;
import io.apicurio.registry.serde.generic.GenericSerializer;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

import static io.apicurio.registry.serde.SerdeConfig.IS_KEY;

public class AvroKafkaSerializer2<DATA> implements Serializer<DATA> {


    private GenericSerializer<Schema, DATA> genericSerializer;


    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        var config = new GenericSerDeConfig((Map<String, Object>) configs);
        config.setConfig(IS_KEY, isKey);

        var datatype = new GenericAvroSerDeDatatype<DATA>();
        datatype.configure(config);

        genericSerializer = new GenericSerializer<>(datatype, null);
        genericSerializer.configure(config);
    }


    @Override
    public byte[] serialize(String topic, DATA data) {
        return genericSerializer.serialize(topic, null, data);
    }


    @Override
    public byte[] serialize(String topic, Headers headers, DATA data) {
        //var gh = new GenericHeaders();
        // TODO: Headers
        return genericSerializer.serialize(topic, headers, data);
    }


    @Override
    public void close() {
        genericSerializer.close();
    }
}
