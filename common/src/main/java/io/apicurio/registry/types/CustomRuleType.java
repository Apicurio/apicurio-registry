
package io.apicurio.registry.types;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

@io.quarkus.runtime.annotations.RegisterForReflection
public enum CustomRuleType {

    webhook("webhook");
    private final String value;
    private final static Map<String, CustomRuleType> CONSTANTS = new HashMap<String, CustomRuleType>();

    static {
        for (CustomRuleType c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private CustomRuleType(String value) {
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
    public static CustomRuleType fromValue(String value) {
        CustomRuleType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
