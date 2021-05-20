
package io.apicurio.multitenant.api.datamodel;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceType {

    MAX_TOTAL_SCHEMAS_COUNT("MAX_TOTAL_SCHEMAS_COUNT"),
    MAX_ARTIFACTS_COUNT("MAX_ARTIFACTS_COUNT"),
    MAX_VERSIONS_PER_ARTIFACT_COUNT("MAX_VERSIONS_PER_ARTIFACT_COUNT"),
    MAX_ARTIFACT_PROPERTIES_COUNT("MAX_ARTIFACT_PROPERTIES_COUNT"),
    MAX_PROPERTY_KEY_SIZE_BYTES("MAX_PROPERTY_KEY_SIZE_BYTES"),
    MAX_PROPERTY_VALUE_SIZE_BYTES("MAX_PROPERTY_VALUE_SIZE_BYTES"),
    MAX_ARTIFACT_LABELS_COUNT("MAX_ARTIFACT_LABELS_COUNT"),
    MAX_LABEL_SIZE_BYTES("MAX_LABEL_SIZE_BYTES"),
    MAX_ARTIFACT_NAME_LENGTH_CHARS("MAX_ARTIFACT_NAME_LENGTH_CHARS"),
    MAX_ARTIFACT_DESCRIPTION_LENGTH_CHARS("MAX_ARTIFACT_DESCRIPTION_LENGTH_CHARS");
    private final String value;
    private final static Map<String, ResourceType> CONSTANTS = new HashMap<String, ResourceType>();

    static {
        for (ResourceType c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private ResourceType(String value) {
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
    public static ResourceType fromValue(String value) {
        ResourceType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
