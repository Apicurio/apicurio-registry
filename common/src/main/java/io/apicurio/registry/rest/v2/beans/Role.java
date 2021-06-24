
package io.apicurio.registry.rest.v2.beans;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

@io.quarkus.runtime.annotations.RegisterForReflection
public enum Role {

    READ_ONLY("READ_ONLY"),
    DEVELOPER("DEVELOPER"),
    ADMIN("ADMIN");
    private final String value;
    private final static Map<String, Role> CONSTANTS = new HashMap<String, Role>();

    static {
        for (Role c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private Role(String value) {
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
    public static Role fromValue(String value) {
        Role constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
