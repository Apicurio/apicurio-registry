package io.apicurio.registry.rules.compatibility;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;

import java.util.Objects;
import java.util.Set;

class ComparableSchema {

    private final FieldList fieldList;

    public ComparableSchema(FieldList fields) {
        this.fieldList = fields;
    }

    /**
     * A schema is compatible with another schema if it only adds columns.
     * The mode of a field can only be changed from REQUIRED to NULLABLE (column relaxation), but not vice-versa.
     * Changing a column type makes the schema incompatible.
     * @param other the schema to remain compatible with
     * @param differences a set of differences which render the schema incompatible
     */
    void checkCompatibilityWith(ComparableSchema other, Set<CompatibilityDifference> differences) {
        compare(this.fieldList, other.fieldList, differences, "/root");
    }

    /**
     * Compares all the fields in both field lists, assuming an identical ordering.
     * @param myFields the field of this schema
     * @param otherFields the fields of the other schema
     * @param differences a set to store eventual incompatibilities
     */
    private void compare(FieldList myFields, FieldList otherFields, Set<CompatibilityDifference> differences, String path) {
        if (myFields == null && otherFields == null) {
            return;
        }
        if (myFields == null) {
            differences.add(new BigquerySchemaCompatibilityDifference("Not compatible with a schema which has subfields.", path));
            return;
        }
        if (otherFields == null) {
            differences.add(new BigquerySchemaCompatibilityDifference("Not compatible with a schema which has no subfields.", path));
            return;
        }
        if (otherFields.size() > myFields.size()) {
            differences.add(new BigquerySchemaCompatibilityDifference("Not compatible with a schema which has more elements.", path));
            return;
        }
        // My fields may have more fields than other fields, so we iterate over the number of other fields.
        for (int i = 0 ; i < otherFields.size() ; i++) {
            Field thisField = myFields.get(i);
            Field otherField = otherFields.get(i);
            CompatibilityDifference diff = compare(thisField, otherField, path);
            if (diff == null) {
                compare(thisField.getSubFields(), otherField.getSubFields(), differences, path + "/" + thisField.getName());
            } else {
                differences.add(diff);
            }
        }
    }

    private BigquerySchemaCompatibilityDifference compare(Field field, Field otherField, String path) {
        if (!Objects.equals(field.getName(), otherField.getName())) {
            String message = String.format("Name %s does not match name %s in other schema.",
                    field.getName(), otherField.getName());
            return new BigquerySchemaCompatibilityDifference(message, path);
        }
        if (!Objects.equals(field.getType(), otherField.getType())) {
            String message = String.format("Type %s of field %s does not match type %s in other schema.",
                    field.getType(), field.getName(), otherField.getType());
            return new BigquerySchemaCompatibilityDifference(message, path);
        }
        if (Objects.equals(field.getMode(), otherField.getMode())
                || (field.getMode().equals(Field.Mode.NULLABLE) && otherField.getMode().equals(Field.Mode.REQUIRED))) {
            return null; // OK
        } else {
            String message = String.format("Mode %s of field %s does not match mode %s in other schema.",
                    field.getMode(), field.getName(), otherField.getMode());
            return new BigquerySchemaCompatibilityDifference(message, path);
        }
    }
}
