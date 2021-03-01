
package io.apicurio.registry.rest.v1.beans;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SearchOver {

    everything("everything"),
    name("name"),
    description("description"),
    labels("labels");
    private final String value;
    private final static Map<String, SearchOver> CONSTANTS = new HashMap<String, SearchOver>();

    static {
        for (SearchOver c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private SearchOver(String value) {
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
    public static SearchOver fromValue(String value) {
        SearchOver constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
