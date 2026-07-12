package io.apicurio.registry.avro.rules.compatibility;

import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityFixSuggestion;
import io.apicurio.registry.rules.violation.RuleViolation;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility.Incompatibility;
import org.apache.avro.SchemaCompatibility.SchemaIncompatibilityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Avro-specific compatibility difference that preserves the Avro incompatibility type and attaches
 * structured fix suggestions.
 */
public class AvroCompatibilityDifference implements CompatibilityDifference {

    private final RuleViolation ruleViolation;

    public AvroCompatibilityDifference(Incompatibility incompatibility) {
        Objects.requireNonNull(incompatibility, "incompatibility");
        SchemaIncompatibilityType type = incompatibility.getType();
        String context = incompatibility.getLocation();
        if (context == null || context.isBlank()) {
            context = "/";
        }
        String description = buildDescription(incompatibility);
        List<CompatibilityFixSuggestion> suggestions = AvroCompatibilityFixSuggestions
                .suggestionsFor(incompatibility);
        this.ruleViolation = new RuleViolation(description, context, type.name(), suggestions);
    }

    private static String buildDescription(Incompatibility incompatibility) {
        String message = incompatibility.getMessage();
        SchemaIncompatibilityType type = incompatibility.getType();
        if (type == SchemaIncompatibilityType.READER_FIELD_MISSING_DEFAULT_VALUE) {
            String field = message == null ? "field" : message;
            return "New field '" + field
                    + "' has no default value. Old messages don't contain this field.";
        }
        if (message == null || message.isBlank()) {
            return type.name();
        }
        return type.name() + ": " + message;
    }

    @Override
    public RuleViolation asRuleViolation() {
        return ruleViolation;
    }

    /**
     * Builds concrete fix suggestions for known Avro {@link SchemaIncompatibilityType}s.
     */
    static final class AvroCompatibilityFixSuggestions {

        private AvroCompatibilityFixSuggestions() {
        }

        static List<CompatibilityFixSuggestion> suggestionsFor(Incompatibility incompatibility) {
            List<CompatibilityFixSuggestion> suggestions = new ArrayList<>();
            SchemaIncompatibilityType type = incompatibility.getType();
            Schema reader = incompatibility.getReaderFragment();
            Schema writer = incompatibility.getWriterFragment();
            String fieldOrDetail = incompatibility.getMessage();

            switch (type) {
                case READER_FIELD_MISSING_DEFAULT_VALUE:
                    suggestions.addAll(missingDefaultSuggestions(fieldOrDetail, reader));
                    break;
                case TYPE_MISMATCH:
                    suggestions.addAll(typeMismatchSuggestions(reader, writer));
                    break;
                case MISSING_ENUM_SYMBOLS:
                    suggestions.addAll(missingEnumSuggestions(fieldOrDetail));
                    break;
                case NAME_MISMATCH:
                    suggestions.addAll(nameMismatchSuggestions(reader, writer));
                    break;
                case FIXED_SIZE_MISMATCH:
                    suggestions.addAll(fixedSizeSuggestions(reader, writer));
                    break;
                case MISSING_UNION_BRANCH:
                    suggestions.addAll(missingUnionBranchSuggestions(writer));
                    break;
                default:
                    suggestions.add(CompatibilityFixSuggestion.of(
                            CompatibilityFixSuggestion.Tier.INFORMATIONAL,
                            "No automatic fix is available for incompatibility type " + type.name()
                                    + ". Review the schema change manually."));
                    break;
            }
            return suggestions;
        }

        private static List<CompatibilityFixSuggestion> missingDefaultSuggestions(String fieldName,
                Schema readerFragment) {
            String name = fieldName == null || fieldName.isBlank() ? "field" : fieldName;
            String avroType = inferTypeName(readerFragment);
            List<CompatibilityFixSuggestion> suggestions = new ArrayList<>();
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.RECOMMENDED,
                    "Add a default value for '" + name + "'",
                    "{\"name\": \"" + name + "\", \"type\": \"" + avroType + "\", \"default\": "
                            + defaultLiteralFor(avroType) + "}"));
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.ACCEPTABLE,
                    "Make '" + name + "' nullable with a null default",
                    "{\"name\": \"" + name + "\", \"type\": [\"null\", \"" + avroType
                            + "\"], \"default\": null}"));
            return suggestions;
        }

        private static List<CompatibilityFixSuggestion> typeMismatchSuggestions(Schema reader, Schema writer) {
            List<CompatibilityFixSuggestion> suggestions = new ArrayList<>();
            String readerType = reader != null ? reader.getType().getName() : "reader";
            String writerType = writer != null ? writer.getType().getName() : "writer";
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.RECOMMENDED,
                    "Widen the reader type back to be compatible with the writer type (" + writerType + ")",
                    "\"type\": \"" + writerType + "\""));
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.ACCEPTABLE,
                    "Use a union that includes both types",
                    "\"type\": [\"null\", \"" + writerType + "\", \"" + readerType + "\"]"));
            return suggestions;
        }

        private static List<CompatibilityFixSuggestion> missingEnumSuggestions(String missingSymbols) {
            List<CompatibilityFixSuggestion> suggestions = new ArrayList<>();
            String symbols = missingSymbols == null ? "<removed-symbols>" : missingSymbols;
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.RECOMMENDED,
                    "Add the removed enum symbol(s) back to the reader schema",
                    "\"symbols\": [ /* include */ " + symbols + " ]"));
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.WORKAROUND,
                    "Switch the compatibility level to FORWARD if old readers are no longer needed",
                    "\"rule\": \"FORWARD\""));
            return suggestions;
        }

        private static List<CompatibilityFixSuggestion> nameMismatchSuggestions(Schema reader, Schema writer) {
            List<CompatibilityFixSuggestion> suggestions = new ArrayList<>();
            String oldName = writer != null ? writer.getName() : "old_name";
            String newName = reader != null ? reader.getName() : "new_name";
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.RECOMMENDED,
                    "Keep the original name, or add an Avro alias for the renamed type/field",
                    "\"name\": \"" + newName + "\", \"aliases\": [\"" + oldName + "\"]"));
            return suggestions;
        }

        private static List<CompatibilityFixSuggestion> fixedSizeSuggestions(Schema reader, Schema writer) {
            List<CompatibilityFixSuggestion> suggestions = new ArrayList<>();
            int expected = writer != null && writer.getType() == Schema.Type.FIXED ? writer.getFixedSize() : -1;
            int found = reader != null && reader.getType() == Schema.Type.FIXED ? reader.getFixedSize() : -1;
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.RECOMMENDED,
                    "Restore the fixed size to match the writer schema",
                    expected >= 0
                            ? "\"type\": \"fixed\", \"size\": " + expected + " /* was " + found + " */"
                            : "\"type\": \"fixed\", \"size\": <writer-size>"));
            return suggestions;
        }

        private static List<CompatibilityFixSuggestion> missingUnionBranchSuggestions(Schema writer) {
            List<CompatibilityFixSuggestion> suggestions = new ArrayList<>();
            String branch = writer != null ? writer.getType().getName() : "branch";
            suggestions.add(CompatibilityFixSuggestion.of(CompatibilityFixSuggestion.Tier.RECOMMENDED,
                    "Add the missing writer union branch to the reader union",
                    "\"type\": [\"null\", \"" + branch + "\" /* plus existing branches */ ]"));
            return suggestions;
        }

        private static String inferTypeName(Schema schema) {
            if (schema == null) {
                return "string";
            }
            if (schema.getType() == Schema.Type.RECORD && !schema.getFields().isEmpty()) {
                // For READER_FIELD_MISSING_DEFAULT_VALUE, the reader fragment is often the parent record.
                // Prefer string as a safe default example type.
                return "string";
            }
            if (schema.getType() == Schema.Type.UNION) {
                for (Schema branch : schema.getTypes()) {
                    if (branch.getType() != Schema.Type.NULL) {
                        return branch.getType().getName();
                    }
                }
            }
            return schema.getType().getName();
        }

        private static String defaultLiteralFor(String avroType) {
            return switch (avroType) {
                case "int", "long", "float", "double" -> "0";
                case "boolean" -> "false";
                case "null" -> "null";
                case "bytes" -> "\"\"";
                default -> "\"\"";
            };
        }
    }
}
