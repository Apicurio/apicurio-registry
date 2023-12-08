package io.apicurio.registry.storage.impl.kafkasql.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public enum ActionType {

    CREATE(1),
    UPDATE(2),
    DELETE(3),
    CLEAR(4),
    IMPORT(5),
    RESET(6),

    /**
     * Deletes ALL user data. Does not delete global data, such as log configuration.
     */
    DELETE_ALL_USER_DATA(7);

    private final byte ord;

    /**
     * Constructor.
     */
    ActionType(int ord) {
        this.ord = (byte) ord;
    }

    public final byte getOrd() {
        return this.ord;
    }

    private static final Map<Byte, ActionType> ordIndex = new HashMap<>();
    private static final Map<String, ActionType> normalizedStringMapping = new HashMap<>();

    static {
        for (ActionType at : ActionType.values()) {
            if (ordIndex.containsKey(at.getOrd())) {
                throw new IllegalArgumentException(String.format("Duplicate ord value %d for ActionType %s", at.getOrd(), at.name()));
            }
            ordIndex.put(at.getOrd(), at);

            // For backwards compatibility
            String normalizedString = at.name().toLowerCase();
            if (normalizedStringMapping.containsKey(normalizedString)) {
                throw new IllegalArgumentException(String.format("Duplicate normalized string value %s for ActionType %s", normalizedString, at.name()));
            }
            normalizedStringMapping.put(normalizedString, at);
        }
    }

    public static ActionType fromOrd(byte ord) {
        ActionType res = ordIndex.get(ord);
        if(res == null) {
            throw new IllegalArgumentException(String.format("Could not find ActionType with ord value %s", ord));
        }
        return res;
    }

    @JsonValue
    public Object serialize() {
        return getOrd();
    }

    @JsonCreator
    public static ActionType deserialize(Object value) {
        if(value instanceof Number) {
            Number num = (Number) value;
            // Sanity check
            if(num.longValue() > Byte.MAX_VALUE) {
                throw new IllegalArgumentException(String.format("Unexpectedly high numeric value %s for ActionType ord", num));
            }
            return fromOrd(num.byteValue());
        }
        // For backwards compatibility
        if(value instanceof String) {
            String normalizedString = ((String) value).toLowerCase();
            ActionType res = normalizedStringMapping.get(normalizedString);
            if(res != null) {
                return res;
            }
        }
        throw new IllegalArgumentException(String.format("Could not deserialize value %s to ActionType", value));
    }
}
