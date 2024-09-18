package io.apicurio.registry.serde.avro;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaType;

public class AvroPulsarSerdeSchema<T> implements Schema<T> {

    private final AvroPulsarSerde<T> serde;

    public AvroPulsarSerdeSchema(AvroPulsarSerde<T> serde) {
        this.serde = serde;
    }

    @Override
    public byte[] encode(T obj) {
        return serde.serialize(obj);
    }

    @Override
    public T decode(byte[] bytes) {
        return serde.deserialize(bytes);
    }

    @Override
    public SchemaInfo getSchemaInfo() {
        return SchemaInfo.builder().name("AvroPulsarSerdeSchema").type(SchemaType.BYTES).schema(new byte[0]) // Schema
                // definition
                // is
                // not
                // required
                // for
                // custom
                // serde.
                .build();
    }

    @Override
    public Schema<T> clone() {
        return this.clone();
    }
}
