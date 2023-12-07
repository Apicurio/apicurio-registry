package io.apicurio.registry.protobuf;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class ProtobufDifference {

    private final String message;

    public static ProtobufDifference from(String message) {
        return new ProtobufDifference(message);
    }

    public ProtobufDifference(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

}
