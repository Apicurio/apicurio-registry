package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DiffTypeDescriptionTest {

    @Test
    public void derivesReadableDescriptionFromConstantName() {
        // Constants without an explicit description fall back to a name-derived, human-readable string.
        assertEquals("Subschema type changed", DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription());
        assertEquals("Array type min items added", DiffType.ARRAY_TYPE_MIN_ITEMS_ADDED.getDescription());
        assertEquals("String type max length decreased",
                DiffType.STRING_TYPE_MAX_LENGTH_DECREASED.getDescription());
    }

    @Test
    public void usesCuratedDescriptionForCommonTypes() {
        // High-frequency diff types provide explicit, curated descriptions that read more naturally
        // than the mechanical fallback (e.g. not "Number type integer required false to true").
        assertEquals("A new required property was added",
                DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED.getDescription());
        assertEquals("Additional properties are no longer allowed",
                DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_TRUE_TO_FALSE.getDescription());
        assertEquals("The value must now be an integer",
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
