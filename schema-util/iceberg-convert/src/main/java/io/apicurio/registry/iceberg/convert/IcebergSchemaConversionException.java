package io.apicurio.registry.iceberg.convert;

/**
 * Thrown when a schema cannot be converted between Avro and Apache Iceberg formats.
 */
public class IcebergSchemaConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IcebergSchemaConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
