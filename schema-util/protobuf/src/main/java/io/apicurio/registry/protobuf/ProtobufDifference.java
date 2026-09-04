package io.apicurio.registry.protobuf;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class ProtobufDifference {

    public enum DifferenceType {
        RESERVED_FIELD_USED,
        RESERVED_FIELD_REMOVED,
        FIELD_REMOVED_WITHOUT_RESERVE,
        FIELD_ID_CHANGED,
        FIELD_TYPE_CHANGED,
        FIELD_LABEL_CHANGED,
        FIELD_NAME_CHANGED,
        SERVICE_RPC_REMOVED,
        SERVICE_RPC_SIGNATURE_CHANGED,
        REQUIRED_FIELD_ADDED
    }

    private final String message;
    private final DifferenceType type;

    public static ProtobufDifference from(String message, DifferenceType type) {
        return new ProtobufDifference(message, type);
    }

    public static ProtobufDifference from(String message) {
        return new ProtobufDifference(message, null);
    }

    public ProtobufDifference(String message, DifferenceType type) {
        this.message = message;
        this.type = type;
    }

    public ProtobufDifference(String message) {
        this(message, null);
    }

    public String getMessage() {
        return this.message;
    }

    public DifferenceType getType() {
        return this.type;
    }

}
