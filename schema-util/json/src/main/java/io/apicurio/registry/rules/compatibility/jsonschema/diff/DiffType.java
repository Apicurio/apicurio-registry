package io.apicurio.registry.rules.compatibility.jsonschema.diff;

public enum DiffType {

    SUBSCHEMA_TYPE_CHANGED(false),
    SUBSCHEMA_TYPE_CHANGED_TO_EMPTY_OR_TRUE(true),

    OBJECT_TYPE_REQUIRED_PROPERTIES_ADDED(false),
    OBJECT_TYPE_REQUIRED_PROPERTIES_REMOVED(true),
    OBJECT_TYPE_REQUIRED_PROPERTIES_CHANGED(true),
    OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED(false),
    OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_REMOVED(true),

    OBJECT_TYPE_PROPERTY_SCHEMA_ADDED(false),
    OBJECT_TYPE_PROPERTY_SCHEMA_REMOVED(true),

    OBJECT_TYPE_MIN_PROPERTIES_ADDED(false),
    OBJECT_TYPE_MIN_PROPERTIES_REMOVED(true),
    OBJECT_TYPE_MIN_PROPERTIES_INCREASED(false),
    OBJECT_TYPE_MIN_PROPERTIES_DECREASED(true),

    OBJECT_TYPE_MAX_PROPERTIES_ADDED(false),
    OBJECT_TYPE_MAX_PROPERTIES_REMOVED(true),
    OBJECT_TYPE_MAX_PROPERTIES_INCREASED(true),
    OBJECT_TYPE_MAX_PROPERTIES_DECREASED(false),

    OBJECT_TYPE_ADDITIONAL_PROPERTIES_FALSE_TO_TRUE(true),
    OBJECT_TYPE_ADDITIONAL_PROPERTIES_TRUE_TO_FALSE(false),
    OBJECT_TYPE_ADDITIONAL_PROPERTIES_BOOLEAN_UNCHANGED(true),
    OBJECT_TYPE_ADDITIONAL_PROPERTIES_EXTENDED(true),
    OBJECT_TYPE_ADDITIONAL_PROPERTIES_NARROWED(false),
    OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_UNCHANGED(true),
    OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_CHANGED(false),

    OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_ADDED(false),
    OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_REMOVED(true),

    OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_ADDED(false),
    OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_REMOVED(true),
    OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_CHANGED(true),
    OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_MEMBER_ADDED(false),
    OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_MEMBER_REMOVED(true),

    OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_CHANGED(true),
    OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_ADDED(false),
    OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_REMOVED(true),

    OBJECT_TYPE_SCHEMA_DEPENDENCIES_ADDED(false),
    OBJECT_TYPE_SCHEMA_DEPENDENCIES_REMOVED(true),
    OBJECT_TYPE_SCHEMA_DEPENDENCIES_CHANGED(true),
    OBJECT_TYPE_SCHEMA_DEPENDENCIES_MEMBER_ADDED(false),
    OBJECT_TYPE_SCHEMA_DEPENDENCIES_MEMBER_REMOVED(true),

    OBJECT_TYPE_PROPERTY_SCHEMAS_ADDED(false),
    OBJECT_TYPE_PROPERTY_SCHEMAS_REMOVED(true),
    OBJECT_TYPE_PROPERTY_SCHEMAS_CHANGED(false),
    OBJECT_TYPE_PROPERTY_SCHEMAS_MEMBER_ADDED(false),
    OBJECT_TYPE_PROPERTY_SCHEMAS_MEMBER_REMOVED(true),
    OBJECT_TYPE_PROPERTY_SCHEMAS_EXTENDED(true),
    OBJECT_TYPE_PROPERTY_SCHEMAS_NARROWED(false),
    OBJECT_TYPE_PROPERTY_SCHEMAS_NARROWED_COMPATIBLE_WITH_ADDITIONAL_PROPERTIES(true),

    OBJECT_TYPE_PATTERN_PROPERTY_KEYS_ADDED(false),
    OBJECT_TYPE_PATTERN_PROPERTY_KEYS_REMOVED(true),
    OBJECT_TYPE_PATTERN_PROPERTY_KEYS_CHANGED(true),
    OBJECT_TYPE_PATTERN_PROPERTY_KEYS_MEMBER_ADDED(false),
    OBJECT_TYPE_PATTERN_PROPERTY_KEYS_MEMBER_REMOVED(true),

    ARRAY_TYPE_MIN_ITEMS_ADDED(false),
    ARRAY_TYPE_MIN_ITEMS_REMOVED(true),
    ARRAY_TYPE_MIN_ITEMS_INCREASED(false),
    ARRAY_TYPE_MIN_ITEMS_DECREASED(true),

    ARRAY_TYPE_MAX_ITEMS_ADDED(false),
    ARRAY_TYPE_MAX_ITEMS_REMOVED(true),
    ARRAY_TYPE_MAX_ITEMS_INCREASED(true),
    ARRAY_TYPE_MAX_ITEMS_DECREASED(false),

    ARRAY_TYPE_UNIQUE_ITEMS_FALSE_TO_TRUE(false),
    ARRAY_TYPE_UNIQUE_ITEMS_TRUE_TO_FALSE(true),
    ARRAY_TYPE_UNIQUE_ITEMS_BOOLEAN_UNCHANGED(true),

    ARRAY_TYPE_ADDITIONAL_ITEMS_FALSE_TO_TRUE(true),
    ARRAY_TYPE_ADDITIONAL_ITEMS_TRUE_TO_FALSE(false),
    ARRAY_TYPE_ADDITIONAL_ITEMS_BOOLEAN_UNCHANGED(true),
    ARRAY_TYPE_ADDITIONAL_ITEMS_EXTENDED(true),
    ARRAY_TYPE_ADDITIONAL_ITEMS_NARROWED(false),
    ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_UNCHANGED(true),
    ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_CHANGED(false),

    ARRAY_TYPE_ALL_ITEM_SCHEMA_ADDED(false),
    ARRAY_TYPE_ALL_ITEM_SCHEMA_REMOVED(true),

    ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_ADDED(false),
    ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_REMOVED(true),

    ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_ADDED(false),
    ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_REMOVED(true),

    ARRAY_TYPE_ITEMS_SCHEMAS_LENGTH_INCREASED(false),
    ARRAY_TYPE_ITEMS_SCHEMAS_LENGTH_DECREASED(false),

    ARRAY_TYPE_ITEM_SCHEMA_ADDED(false),
    ARRAY_TYPE_ITEM_SCHEMA_REMOVED(false), // TODO where

    ARRAY_TYPE_ITEM_SCHEMAS_EXTENDED(true),
    ARRAY_TYPE_ITEM_SCHEMAS_NARROWED(false),
    ARRAY_TYPE_ITEM_SCHEMAS_NARROWED_COMPATIBLE_WITH_ADDITIONAL_PROPERTIES(true),
    ARRAY_TYPE_ITEM_SCHEMAS_CHANGED(false),

    STRING_TYPE_MIN_LENGTH_ADDED(false),
    STRING_TYPE_MIN_LENGTH_REMOVED(true),
    STRING_TYPE_MIN_LENGTH_INCREASED(false),
    STRING_TYPE_MIN_LENGTH_DECREASED(true),

    STRING_TYPE_MAX_LENGTH_ADDED(false),
    STRING_TYPE_MAX_LENGTH_REMOVED(true),
    STRING_TYPE_MAX_LENGTH_INCREASED(true),
    STRING_TYPE_MAX_LENGTH_DECREASED(false),

    STRING_TYPE_PATTERN_ADDED(false),
    STRING_TYPE_PATTERN_REMOVED(true),
    STRING_TYPE_PATTERN_CHANGED(false),

    STRING_TYPE_FORMAT_ADDED(false),
    STRING_TYPE_FORMAT_REMOVED(true),
    STRING_TYPE_FORMAT_CHANGED(false),

    STRING_TYPE_CONTENT_ENCODING_ADDED(false),
    STRING_TYPE_CONTENT_ENCODING_REMOVED(true),
    STRING_TYPE_CONTENT_ENCODING_CHANGED(false),

    STRING_TYPE_CONTENT_MEDIA_TYPE_ADDED(false),
    STRING_TYPE_CONTENT_MEDIA_TYPE_REMOVED(true),
    STRING_TYPE_CONTENT_MEDIA_TYPE_CHANGED(false),

    CONST_TYPE_VALUE_CHANGED(false),

    ENUM_TYPE_VALUES_CHANGED(true),
    ENUM_TYPE_VALUES_MEMBER_ADDED(true),
    ENUM_TYPE_VALUES_MEMBER_REMOVED(false),

    NUMBER_TYPE_MINIMUM_ADDED(false),
    NUMBER_TYPE_MINIMUM_REMOVED(true),
    NUMBER_TYPE_MINIMUM_INCREASED(false),
    NUMBER_TYPE_MINIMUM_DECREASED(true),

    NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_FALSE_TO_TRUE(false),
    NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_TRUE_TO_FALSE(true),
    NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_UNCHANGED(true),

    NUMBER_TYPE_EXCLUSIVE_MINIMUM_ADDED(false),
    NUMBER_TYPE_EXCLUSIVE_MINIMUM_REMOVED(true),
    NUMBER_TYPE_EXCLUSIVE_MINIMUM_INCREASED(false),
    NUMBER_TYPE_EXCLUSIVE_MINIMUM_DECREASED(true),

    NUMBER_TYPE_MAXIMUM_ADDED(false),
    NUMBER_TYPE_MAXIMUM_REMOVED(true),
    NUMBER_TYPE_MAXIMUM_INCREASED(true),
    NUMBER_TYPE_MAXIMUM_DECREASED(false),

    NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_FALSE_TO_TRUE(false),
    NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_TRUE_TO_FALSE(true),
    NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_UNCHANGED(true),

    NUMBER_TYPE_EXCLUSIVE_MAXIMUM_ADDED(false),
    NUMBER_TYPE_EXCLUSIVE_MAXIMUM_REMOVED(true),
    NUMBER_TYPE_EXCLUSIVE_MAXIMUM_INCREASED(true),
    NUMBER_TYPE_EXCLUSIVE_MAXIMUM_DECREASED(false),

    NUMBER_TYPE_MULTIPLE_OF_ADDED(false),
    NUMBER_TYPE_MULTIPLE_OF_REMOVED(true),
    NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_DIVISIBLE(true),
    NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_NOT_DIVISIBLE(false),

    NUMBER_TYPE_INTEGER_REQUIRED_FALSE_TO_TRUE(false),
    NUMBER_TYPE_INTEGER_REQUIRED_TRUE_TO_FALSE(true),
    NUMBER_TYPE_INTEGER_REQUIRED_UNCHANGED(true),

    COMBINED_TYPE_CRITERION_EXTENDED(true),
    COMBINED_TYPE_CRITERION_NARROWED(false),
    COMBINED_TYPE_CRITERION_CHANGED(false),

    COMBINED_TYPE_ONE_OF_SIZE_INCREASED(true), // As long as the existing sub-schemas maintain compatibility, checked separately.
    COMBINED_TYPE_ONE_OF_SIZE_DECREASED(false),

    COMBINED_TYPE_ALL_OF_SIZE_INCREASED(false),
    COMBINED_TYPE_ALL_OF_SIZE_DECREASED(true),

    COMBINED_TYPE_ANY_OF_SIZE_INCREASED(true), // As long as the existing sub-schemas maintain compatibility, checked separately.
    COMBINED_TYPE_ANY_OF_SIZE_DECREASED(false),

    COMBINED_TYPE_SUBSCHEMA_NOT_COMPATIBLE(false),

    CONDITIONAL_TYPE_IF_SCHEMA_ADDED(false),
    CONDITIONAL_TYPE_IF_SCHEMA_REMOVED(false),
    CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BOTH(true),
    CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD(false),
    CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD(false),
    CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_NONE(false),

    CONDITIONAL_TYPE_THEN_SCHEMA_ADDED(false),
    CONDITIONAL_TYPE_THEN_SCHEMA_REMOVED(true),
    CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BOTH(true),
    CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD(true),
    CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD(false),
    CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_NONE(false),

    CONDITIONAL_TYPE_ELSE_SCHEMA_ADDED(false),
    CONDITIONAL_TYPE_ELSE_SCHEMA_REMOVED(true),
    CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BOTH(true),
    CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD(true),
    CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD(false),
    CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_NONE(false),

    REFERENCE_TYPE_TARGET_SCHEMA_ADDED(false),
    REFERENCE_TYPE_TARGET_SCHEMA_REMOVED(false), // TODO Would this cause validation error?

    NOT_TYPE_SCHEMA_COMPATIBLE_BOTH(true),
    NOT_TYPE_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD(false),
    NOT_TYPE_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD(true),
    NOT_TYPE_SCHEMA_COMPATIBLE_NONE(false),

    UNDEFINED_UNUSED(false); // Should not be used.

    private String description;

    private final boolean backwardsCompatible;

    DiffType(boolean backwardsCompatible) {
        this.description = this.toString();
        this.backwardsCompatible = backwardsCompatible;
    }

    DiffType(String description, boolean backwardsCompatible) {
        this(backwardsCompatible);
        this.description = description;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the backwardsCompatible
     */
    public boolean isBackwardsCompatible() {
        return backwardsCompatible;
    }

}
