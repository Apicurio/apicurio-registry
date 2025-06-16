package io.apicurio.registry.utils.converter.avro;

import org.apache.avro.LogicalType;
import org.apache.kafka.connect.data.Schema;

/**
 * The Apicurio AvroConverter may be extending to support more avro logical type conversions
 * from 3rd party kafka connect schemas, e.g. debezium types.
 *
 * <p>Apicurio {@link AvroData} may discover implementations of this interface using the Java {@link java.util.ServiceLoader} mechanism.
 * To support this, implementations of this interface should also contain a service provider configuration file in
 * {@code META-INF/services/io.apicurio.registry.utils.converter.avro.ToAvroLogicalTypeConverter}.
 * <p>
 * Existing default converters in {@link AvroData} can be extended or replaced with just registering a new converter for the same kafka connect type.
 */
public interface ToAvroLogicalTypeConverter extends LogicalTypeConverter {

    String kafkaConnectLogicalTypeName();

    LogicalType avroLogicalType(Schema schema);
}

