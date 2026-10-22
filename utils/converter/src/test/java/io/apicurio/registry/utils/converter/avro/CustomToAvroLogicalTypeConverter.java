package io.apicurio.registry.utils.converter.avro;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.DataException;

public class CustomToAvroLogicalTypeConverter implements ToAvroLogicalTypeConverter {
    @Override
    public String kafkaConnectLogicalTypeName() {
        return CustomKafkaConnectLogicalType.LOGICAL_NAME;
    }

    @Override
    public LogicalType avroLogicalType(Schema schema) {
        return LogicalTypes.timestampMicros();
    }

    @Override
    public Object convert(Schema schema, Object value) {
        if (!(value instanceof Long)) {
            throw new DataException(
                    "Invalid type for io.debezium.time.MicroTimestamp, expected int64 but was " + value.getClass());
        }
        return value;
    }
}
