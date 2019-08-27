
package io.apicurio.registry.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

public enum ArtifactType {

    avro("avro"),
    protobuf("protobuf"),
    json("json"),
    openapi("openapi"),
    asyncapi("asyncapi");

    private final static Map<String, ArtifactType> CONSTANTS = new HashMap<String, ArtifactType>();

    private final String value;

    static {
        for (ArtifactType c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private ArtifactType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
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
