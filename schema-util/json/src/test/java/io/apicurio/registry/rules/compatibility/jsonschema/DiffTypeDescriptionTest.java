package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DiffTypeDescriptionTest {

    @Test
    public void derivesReadableDescriptionFromConstantName() {
        assertEquals("Subschema type changed", DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription());
        assertEquals("Object type required properties member added",
                DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED.getDescription());
        assertEquals("Number type integer required false to true",
                DiffType.NUMBER_TYPE_INTEGER_REQUIRED_FALSE_TO_TRUE.getDescription());
    }

    @Test
    public void noConstantExposesRawEnumName() {
        for (DiffType type : DiffType.values()) {
            String description = type.getDescription();
            assertNotEquals(type.name(), description,
                    "Description for " + type.name() + " must not be the raw enum name");
            assertFalse(description.contains("_"),
                    "Description for " + type.name() + " must not contain underscores: " + description);
        }
    }
}
