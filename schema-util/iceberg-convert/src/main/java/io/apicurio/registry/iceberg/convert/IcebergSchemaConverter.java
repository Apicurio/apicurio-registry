package io.apicurio.registry.iceberg.convert;

import org.apache.avro.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.avro.AvroSchemaUtil;

/**
 * Converts schemas between Apache Avro and Apache Iceberg schema JSON, in both directions.
 *
 * The conversion delegates to Iceberg's own Avro integration
 * ({@link org.apache.iceberg.avro.AvroSchemaUtil} and {@link org.apache.iceberg.SchemaParser}),
 * which handles the primitive and logical type mapping (string, int, long, float, double, boolean,
 * bytes, date, timestamp, decimal), nested structures (record/struct, array/list, map) and Iceberg
 * field IDs.
 *
 * Known limitations:
 * <ul>
 *   <li>The top-level Avro type must be a {@code record} when converting Avro to Iceberg.</li>
 *   <li>Iceberg stores timestamps at microsecond precision, so an Avro {@code timestamp-millis}
 *       round-trips through Iceberg as microseconds (precision widening).</li>
 *   <li>Avro maps only support string keys; an Iceberg map with a non-string key type cannot be
 *       represented faithfully in Avro.</li>
 *   <li>Iceberg has no union type. A nullable Avro union {@code ["null", T]} becomes an optional
 *       field of type {@code T}; any other (polymorphic) union is modeled by Iceberg's Avro
 *       integration as a tagged-union struct (a {@code tag} field plus one optional member field
 *       per branch).</li>
 * </ul>
 *
 * When converting Iceberg to Avro, the emitted Avro fields carry a {@code field-id} property so
 * that the Iceberg field IDs are preserved.
 */
public final class IcebergSchemaConverter {

    /**
     * Default record name used for the top-level Avro record produced when converting an Iceberg
     * schema that does not otherwise carry a name.
     */
    public static final String DEFAULT_RECORD_NAME = "record";

    private IcebergSchemaConverter() {
    }

    /**
     * Converts an Avro schema (JSON) to an Iceberg schema (JSON).
     *
     * @param avroSchemaJson the Avro schema as a JSON string; the top-level type must be a record
     * @return the equivalent Iceberg schema as a pretty-printed JSON string
     * @throws IcebergSchemaConversionException if the input is not a valid, convertible Avro schema
     */
    public static String avroToIceberg(String avroSchemaJson) {
        try {
            Schema avroSchema = new Schema.Parser().parse(avroSchemaJson);
            org.apache.iceberg.Schema icebergSchema = AvroSchemaUtil.toIceberg(avroSchema);
            return SchemaParser.toJson(icebergSchema, true);
        } catch (RuntimeException e) {
            throw new IcebergSchemaConversionException("Failed to convert Avro schema to Iceberg", e);
        }
    }

    /**
     * Converts an Iceberg schema (JSON) to an Avro schema (JSON), using {@link #DEFAULT_RECORD_NAME}
     * as the top-level record name.
     *
     * @param icebergSchemaJson the Iceberg schema as a JSON string
     * @return the equivalent Avro schema as a pretty-printed JSON string, with field IDs preserved
     * @throws IcebergSchemaConversionException if the input is not a valid, convertible Iceberg schema
     */
    public static String icebergToAvro(String icebergSchemaJson) {
        return icebergToAvro(icebergSchemaJson, DEFAULT_RECORD_NAME);
    }

    /**
     * Converts an Iceberg schema (JSON) to an Avro schema (JSON).
     *
     * @param icebergSchemaJson the Iceberg schema as a JSON string
     * @param recordName the name to assign to the top-level Avro record
     * @return the equivalent Avro schema as a pretty-printed JSON string, with field IDs preserved
     * @throws IcebergSchemaConversionException if the input is not a valid, convertible Iceberg schema
     */
    public static String icebergToAvro(String icebergSchemaJson, String recordName) {
        try {
            org.apache.iceberg.Schema icebergSchema = SchemaParser.fromJson(icebergSchemaJson);
            Schema avroSchema = AvroSchemaUtil.convert(icebergSchema, recordName);
            return avroSchema.toString(true);
        } catch (RuntimeException e) {
            throw new IcebergSchemaConversionException("Failed to convert Iceberg schema to Avro", e);
        }
    }
}
