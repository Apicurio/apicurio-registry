
package io.apicurio.multitenant.api.datamodel;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TenantStatusValue {

    READY("READY"),
    TO_BE_DELETED("TO_BE_DELETED"),
    DELETED("DELETED");
    private final String value;
    private final static Map<String, TenantStatusValue> CONSTANTS = new HashMap<String, TenantStatusValue>();

    static {
        for (TenantStatusValue c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private TenantStatusValue(String value) {
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
    public static TenantStatusValue fromValue(String value) {
        TenantStatusValue constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
