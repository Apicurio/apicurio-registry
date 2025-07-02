package io.apicurio.registry.utils.converter.avro;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;

public class CustomKafkaConnectLogicalType {
    public static final String LOGICAL_NAME = "io.apicurio.registry.utils.converter.avro.CustomKafkaConnectLogicalType";

    public static SchemaBuilder builder() {
        return SchemaBuilder.int64()
                .name(LOGICAL_NAME)
                .version(1);
    }

    public static final Schema SCHEMA = builder().schema();
}
