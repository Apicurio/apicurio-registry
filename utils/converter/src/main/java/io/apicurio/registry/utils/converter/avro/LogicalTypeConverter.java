package io.apicurio.registry.utils.converter.avro;

import org.apache.kafka.connect.data.Schema;

/**
 * Base interface for declaring unidirectional conversion between Avro and KafkaConnect logical types,
 * e.g. {@link org.apache.kafka.connect.data.Timestamp} to avro logical type {@code 'timestamp-millis'}.
 * <p>
 * The implementation must handle any differences in the underlying format.
 */
interface LogicalTypeConverter {

    Object convert(Schema schema, Object value);
}