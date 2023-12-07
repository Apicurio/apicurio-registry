package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;

public interface AvroDatumProvider<T> {

    default void configure(AvroKafkaSerdeConfig config) {}

    DatumWriter<T> createDatumWriter(T data, Schema schema);

    DatumReader<T> createDatumReader(Schema schema);

    Schema toSchema(T data);
}
