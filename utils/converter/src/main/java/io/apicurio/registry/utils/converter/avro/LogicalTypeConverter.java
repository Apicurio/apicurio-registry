package io.apicurio.registry.utils.converter.avro;

import org.apache.kafka.connect.data.Schema;

interface LogicalTypeConverter {

    Object convert(Schema schema, Object value);
}