
package io.apicurio.registry.rest.v1.beans;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IfExistsType {

    FAIL("FAIL"),
    UPDATE("UPDATE"),
    RETURN("RETURN"),
    RETURN_OR_UPDATE("RETURN_OR_UPDATE");
    private final String value;
    private final static Map<String, IfExistsType> CONSTANTS = new HashMap<String, IfExistsType>();

    static {
        for (IfExistsType c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private IfExistsType(String value) {
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
    public static IfExistsType fromValue(String value) {
        IfExistsType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
