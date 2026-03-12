package io.apicurio.registry.serde.kafka.data;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.data.SerdeMetadata;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka specific implementation for the Record Metadata abstraction used by the SchemaResolver
 */
public class KafkaSerdeMetadata extends SerdeMetadata {

    private final Headers headers;
    private String explicitSchemaContent;
    private String explicitSchemaType;

    public KafkaSerdeMetadata(String topic, boolean isKey, Headers headers) {
        super(topic, isKey);
        this.headers = headers;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Metadata#artifactReference()
     */
    @Override
    public ArtifactReference artifactReference() {
        return null;
    }

    /**
     * @return the headers
     */
    public Headers getHeaders() {
        return headers;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Metadata#explicitSchemaContent()
     */
    @Override
    public String explicitSchemaContent() {
        return explicitSchemaContent;
    }

    /**
     * Sets the explicit schema content.
     *
     * @param explicitSchemaContent the schema content read from headers
     */
    public void setExplicitSchemaContent(String explicitSchemaContent) {
        this.explicitSchemaContent = explicitSchemaContent;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Metadata#explicitSchemaType()
     */
    @Override
    public String explicitSchemaType() {
        return explicitSchemaType;
    }

    /**
     * Sets the explicit schema type.
     *
     * @param explicitSchemaType the schema type read from headers (e.g., "AVRO", "PROTOBUF", "JSON")
     */
    public void setExplicitSchemaType(String explicitSchemaType) {
        this.explicitSchemaType = explicitSchemaType;
    }

}
