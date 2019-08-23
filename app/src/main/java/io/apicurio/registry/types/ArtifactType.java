
package io.apicurio.registry.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

public enum ArtifactType {

    avro("avro", new AvroArtifactTypeAdapter()),
    protobuf("protobuf", new ProtobufArtifactTypeAdapter()),
    json("json", new JsonArtifactTypeAdapter()),
    openapi("openapi", NoopArtifactTypeAdapter.INSTANCE),
    asyncapi("asyncapi", NoopArtifactTypeAdapter.INSTANCE);

    private final static Map<String, ArtifactType> CONSTANTS = new HashMap<String, ArtifactType>();

    private final String value;
    private transient ArtifactTypeAdapter adapter;

    static {
        for (ArtifactType c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private ArtifactType(String value, ArtifactTypeAdapter adapter) {
        this.value = value;
        this.adapter = adapter;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonIgnore
    public ArtifactTypeAdapter getAdapter() {
        return adapter;
    }

    @JsonCreator
    public static ArtifactType fromValue(String value) {
        ArtifactType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
