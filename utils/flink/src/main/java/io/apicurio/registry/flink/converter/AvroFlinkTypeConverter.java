package io.apicurio.registry.flink.converter;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Apache Avro schemas to Flink DataTypes.
 */
public final class AvroFlinkTypeConverter {

    /** Precision for millisecond-based time types. */
    private static final int MILLIS_PRECISION = 3;

    /** Precision for microsecond-based time types. */
    private static final int MICROS_PRECISION = 6;

    private AvroFlinkTypeConverter() {
    }

    /**
     * Converts an Avro schema string to a Flink ResolvedSchema.
     *
     * @param avroSchemaString the Avro schema as JSON string
     * @return the Flink ResolvedSchema
     */
    public static ResolvedSchema convert(final String avroSchemaString) {
        final Schema avroSchema = new Schema.Parser().parse(avroSchemaString);
        return convertRecord(avroSchema);
    }

    private static ResolvedSchema convertRecord(final Schema avroSchema) {
        if (avroSchema.getType() != Schema.Type.RECORD) {
            throw new IllegalArgumentException(
                    "Expected RECORD schema, got: " + avroSchema.getType());
        }
        final List<Column> columns = new ArrayList<>();
        for (Schema.Field field : avroSchema.getFields()) {
            final DataType dataType = convertType(field.schema());
            columns.add(Column.physical(field.name(), dataType));
        }
        return ResolvedSchema.of(columns);
    }

    /**
     * Converts an Avro schema to a Flink DataType.
     *
     * @param avroSchema the Avro schema
     * @return the Flink DataType
     */
    public static DataType convertType(final Schema avroSchema) {
        final LogicalType logicalType = avroSchema.getLogicalType();
        if (logicalType != null) {
            return convertLogicalType(avroSchema, logicalType);
        }
        return convertPrimitiveType(avroSchema);
    }

    private static DataType convertPrimitiveType(final Schema avroSchema) {
        switch (avroSchema.getType()) {
            case NULL:
                return DataTypes.NULL();
            case BOOLEAN:
                return DataTypes.BOOLEAN();
            case INT:
                return DataTypes.INT();
            case LONG:
                return DataTypes.BIGINT();
            case FLOAT:
                return DataTypes.FLOAT();
            case DOUBLE:
                return DataTypes.DOUBLE();
            case STRING:
                return DataTypes.STRING();
            case BYTES:
                return DataTypes.BYTES();
            case FIXED:
                return DataTypes.BINARY(avroSchema.getFixedSize());
            case ENUM:
                return DataTypes.STRING();
            case ARRAY:
                return DataTypes.ARRAY(
                        convertType(avroSchema.getElementType()));
            case MAP:
                return DataTypes.MAP(
                        DataTypes.STRING(),
                        convertType(avroSchema.getValueType()));
            case RECORD:
                return convertRecordToRow(avroSchema);
            case UNION:
                return convertUnion(avroSchema);
            default:
                throw new IllegalArgumentException(
                        "Unsupported Avro type: " + avroSchema.getType());
        }
    }

    private static DataType convertLogicalType(
            final Schema avroSchema,
            final LogicalType logicalType) {
        final String logicalTypeName = logicalType.getName();
        switch (logicalTypeName) {
            case "date":
                return DataTypes.DATE();
            case "time-millis":
                return DataTypes.TIME(MILLIS_PRECISION);
            case "time-micros":
                return DataTypes.TIME(MICROS_PRECISION);
            case "timestamp-millis":
                return DataTypes.TIMESTAMP(MILLIS_PRECISION);
            case "timestamp-micros":
                return DataTypes.TIMESTAMP(MICROS_PRECISION);
            case "local-timestamp-millis":
                return DataTypes.TIMESTAMP_LTZ(MILLIS_PRECISION);
            case "local-timestamp-micros":
                return DataTypes.TIMESTAMP_LTZ(MICROS_PRECISION);
            case "decimal":
                final LogicalTypes.Decimal decimal =
                        (LogicalTypes.Decimal) logicalType;
                return DataTypes.DECIMAL(
                        decimal.getPrecision(), decimal.getScale());
            case "uuid":
                return DataTypes.STRING();
            case "duration":
                return DataTypes.INTERVAL(
                        DataTypes.MONTH(),
                        DataTypes.SECOND(MILLIS_PRECISION));
            default:
                return convertPrimitiveType(avroSchema);
        }
    }

    private static DataType convertRecordToRow(final Schema avroSchema) {
        final List<DataTypes.Field> fields = new ArrayList<>();
        for (Schema.Field field : avroSchema.getFields()) {
            final DataType fieldType = convertType(field.schema());
            fields.add(DataTypes.FIELD(field.name(), fieldType));
        }
        return DataTypes.ROW(fields.toArray(new DataTypes.Field[0]));
    }

    private static DataType convertUnion(final Schema avroSchema) {
        final List<Schema> types = avroSchema.getTypes();
        final List<Schema> nonNullTypes = new ArrayList<>();
        boolean hasNull = false;

        for (Schema type : types) {
            if (type.getType() == Schema.Type.NULL) {
                hasNull = true;
            } else {
                nonNullTypes.add(type);
            }
        }

        if (nonNullTypes.isEmpty()) {
            return DataTypes.NULL();
        }
        if (nonNullTypes.size() == 1) {
            final DataType dataType = convertType(nonNullTypes.get(0));
            return hasNull ? dataType.nullable() : dataType.notNull();
        }
        return DataTypes.STRING();
    }
}
