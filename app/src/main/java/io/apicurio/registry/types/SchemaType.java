package io.apicurio.registry.types;

/**
 * @author Ales Justin
 */
public enum SchemaType {
    AVRO(new AvroSchemaTypeAdapter()),
    JSON(NoopSchemaTypeAdapter.INSTANCE),
    PROTOBUF(NoopSchemaTypeAdapter.INSTANCE),
    OPEN_API(NoopSchemaTypeAdapter.INSTANCE),
    ASYNC_API(NoopSchemaTypeAdapter.INSTANCE),
    THRIFT(NoopSchemaTypeAdapter.INSTANCE);

    private SchemaTypeAdapter adapter;

    SchemaType(SchemaTypeAdapter adapter) {
        this.adapter = adapter;
    }

    public SchemaTypeAdapter getAdapter() {
        return adapter;
    }
}
