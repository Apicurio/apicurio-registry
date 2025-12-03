package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

/**
 * Converts a FileDescriptor back to .proto text format.
 * This is a simplified implementation that reconstructs .proto syntax from FileDescriptor.
 * It handles common protobuf features but may not cover all edge cases.
 */
public class FileDescriptorToProtoConverter {

    public static String convert(Descriptors.FileDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        DescriptorProtos.FileDescriptorProto proto = descriptor.toProto();

        // Syntax
        String syntax = proto.getSyntax();
        boolean isProto3 = syntax.isEmpty() || "proto3".equals(syntax);

        if (!syntax.isEmpty()) {
            sb.append("syntax = \"").append(syntax).append("\";\n");
            sb.append("\n");
        }

        // Package
        if (proto.hasPackage() && !proto.getPackage().isEmpty()) {
            sb.append("package ").append(proto.getPackage()).append(";\n\n");
        }

        // File Options
        DescriptorProtos.FileOptions fileOptions = proto.getOptions();
        boolean hasOptions = false;

        if (fileOptions.hasJavaPackage()) {
            sb.append("option java_package = \"").append(fileOptions.getJavaPackage()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasJavaOuterClassname()) {
            sb.append("option java_outer_classname = \"").append(fileOptions.getJavaOuterClassname()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasJavaMultipleFiles()) {
            sb.append("option java_multiple_files = ").append(fileOptions.getJavaMultipleFiles()).append(";\n");
            hasOptions = true;
        }
        if (fileOptions.hasJavaStringCheckUtf8()) {
            sb.append("option java_string_check_utf8 = ").append(fileOptions.getJavaStringCheckUtf8()).append(";\n");
            hasOptions = true;
        }
        if (fileOptions.hasOptimizeFor()) {
            sb.append("option optimize_for = ").append(fileOptions.getOptimizeFor().name()).append(";\n");
            hasOptions = true;
        }
        if (fileOptions.hasGoPackage()) {
            sb.append("option go_package = \"").append(fileOptions.getGoPackage()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasCcGenericServices()) {
            sb.append("option cc_generic_services = ").append(fileOptions.getCcGenericServices()).append(";\n");
            hasOptions = true;
        }
        if (fileOptions.hasJavaGenericServices()) {
            sb.append("option java_generic_services = ").append(fileOptions.getJavaGenericServices()).append(";\n");
            hasOptions = true;
        }
        if (fileOptions.hasPyGenericServices()) {
            sb.append("option py_generic_services = ").append(fileOptions.getPyGenericServices()).append(";\n");
            hasOptions = true;
        }
        // Note: php_generic_services was deprecated and removed in newer protobuf versions
        if (fileOptions.hasDeprecated() && fileOptions.getDeprecated()) {
            sb.append("option deprecated = true;\n");
            hasOptions = true;
        }
        if (fileOptions.hasCcEnableArenas()) {
            sb.append("option cc_enable_arenas = ").append(fileOptions.getCcEnableArenas()).append(";\n");
            hasOptions = true;
        }
        if (fileOptions.hasObjcClassPrefix()) {
            sb.append("option objc_class_prefix = \"").append(fileOptions.getObjcClassPrefix()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasCsharpNamespace()) {
            sb.append("option csharp_namespace = \"").append(fileOptions.getCsharpNamespace()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasSwiftPrefix()) {
            sb.append("option swift_prefix = \"").append(fileOptions.getSwiftPrefix()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasPhpClassPrefix()) {
            sb.append("option php_class_prefix = \"").append(fileOptions.getPhpClassPrefix()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasPhpNamespace()) {
            sb.append("option php_namespace = \"").append(fileOptions.getPhpNamespace()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasPhpMetadataNamespace()) {
            sb.append("option php_metadata_namespace = \"").append(fileOptions.getPhpMetadataNamespace()).append("\";\n");
            hasOptions = true;
        }
        if (fileOptions.hasRubyPackage()) {
            sb.append("option ruby_package = \"").append(fileOptions.getRubyPackage()).append("\";\n");
            hasOptions = true;
        }

        if (hasOptions) {
            sb.append("\n");
        }

        // Imports
        // Need to track which imports are public or weak
        java.util.Set<Integer> publicDeps = new java.util.HashSet<>();
        java.util.Set<Integer> weakDeps = new java.util.HashSet<>();

        for (int publicIndex : proto.getPublicDependencyList()) {
            publicDeps.add(publicIndex);
        }
        for (int weakIndex : proto.getWeakDependencyList()) {
            weakDeps.add(weakIndex);
        }

        for (int i = 0; i < proto.getDependencyCount(); i++) {
            String dependency = proto.getDependency(i);
            sb.append("import ");
            if (publicDeps.contains(i)) {
                sb.append("public ");
            } else if (weakDeps.contains(i)) {
                sb.append("weak ");
            }
            sb.append("\"").append(dependency).append("\";\n");
        }
        if (proto.getDependencyCount() > 0) {
            sb.append("\n");
        }

        // Messages
        for (DescriptorProtos.DescriptorProto messageType : proto.getMessageTypeList()) {
            convertMessage(sb, messageType, 0, isProto3);
        }

        // Enums
        for (DescriptorProtos.EnumDescriptorProto enumType : proto.getEnumTypeList()) {
            convertEnum(sb, enumType, 0);
        }

        // Services
        for (DescriptorProtos.ServiceDescriptorProto service : proto.getServiceList()) {
            convertService(sb, service);
        }

        return sb.toString();
    }

    private static void convertMessage(StringBuilder sb, DescriptorProtos.DescriptorProto message, int indent, boolean isProto3) {
        indent(sb, indent);
        sb.append("message ").append(message.getName()).append(" {\n");

        // Message options
        if (message.hasOptions()) {
            DescriptorProtos.MessageOptions messageOptions = message.getOptions();
            if (messageOptions.hasMessageSetWireFormat() && messageOptions.getMessageSetWireFormat()) {
                indent(sb, indent + 1);
                sb.append("option message_set_wire_format = true;\n");
            }
            if (messageOptions.hasNoStandardDescriptorAccessor() && messageOptions.getNoStandardDescriptorAccessor()) {
                indent(sb, indent + 1);
                sb.append("option no_standard_descriptor_accessor = true;\n");
            }
            if (messageOptions.hasDeprecated() && messageOptions.getDeprecated()) {
                indent(sb, indent + 1);
                sb.append("option deprecated = true;\n");
            }
            // Note: map_entry is synthetic and shouldn't be rendered
        }

        // Reserved fields
        for (DescriptorProtos.DescriptorProto.ReservedRange range : message.getReservedRangeList()) {
            indent(sb, indent + 1);
            sb.append("reserved ");
            if (range.getStart() == range.getEnd() - 1) {
                sb.append(range.getStart());
            } else {
                sb.append(range.getStart()).append(" to ").append(range.getEnd() - 1);
            }
            sb.append(";\n");
        }
        for (String reservedName : message.getReservedNameList()) {
            indent(sb, indent + 1);
            sb.append("reserved \"").append(reservedName).append("\";\n");
        }

        // Extension ranges
        for (DescriptorProtos.DescriptorProto.ExtensionRange range : message.getExtensionRangeList()) {
            indent(sb, indent + 1);
            sb.append("extensions ");
            if (range.getStart() == range.getEnd() - 1) {
                sb.append(range.getStart());
            } else if (range.getEnd() >= 536870912) { // 2^29, which is max field number
                sb.append(range.getStart()).append(" to max");
            } else {
                sb.append(range.getStart()).append(" to ").append(range.getEnd() - 1);
            }
            sb.append(";\n");
        }

        // Nested enums
        for (DescriptorProtos.EnumDescriptorProto enumType : message.getEnumTypeList()) {
            convertEnum(sb, enumType, indent + 1);
        }

        // Nested messages
        for (DescriptorProtos.DescriptorProto nestedType : message.getNestedTypeList()) {
            // Skip map entry messages
            if (!nestedType.getOptions().getMapEntry()) {
                convertMessage(sb, nestedType, indent + 1, isProto3);
            }
        }

        // Group fields by oneof index for proper oneof rendering
        // Fields with oneof_index set belong to a oneof, others are regular fields
        java.util.Map<Integer, java.util.List<DescriptorProtos.FieldDescriptorProto>> oneofFields = new java.util.HashMap<>();
        java.util.List<DescriptorProtos.FieldDescriptorProto> regularFields = new java.util.ArrayList<>();

        for (DescriptorProtos.FieldDescriptorProto field : message.getFieldList()) {
            if (field.hasOneofIndex()) {
                oneofFields.computeIfAbsent(field.getOneofIndex(), k -> new java.util.ArrayList<>()).add(field);
            } else {
                regularFields.add(field);
            }
        }

        // Render regular fields
        for (DescriptorProtos.FieldDescriptorProto field : regularFields) {
            convertField(sb, field, indent + 1, isProto3);
        }

        // Render oneof fields
        for (int oneofIndex = 0; oneofIndex < message.getOneofDeclCount(); oneofIndex++) {
            DescriptorProtos.OneofDescriptorProto oneofDecl = message.getOneofDecl(oneofIndex);
            java.util.List<DescriptorProtos.FieldDescriptorProto> fields = oneofFields.get(oneofIndex);

            if (fields != null && !fields.isEmpty()) {
                indent(sb, indent + 1);
                sb.append("oneof ").append(oneofDecl.getName()).append(" {\n");

                for (DescriptorProtos.FieldDescriptorProto field : fields) {
                    convertFieldWithinOneof(sb, field, indent + 2, isProto3);
                }

                indent(sb, indent + 1);
                sb.append("}\n");
            }
        }

        indent(sb, indent);
        sb.append("}\n");
    }

    private static void convertField(StringBuilder sb, DescriptorProtos.FieldDescriptorProto field, int indent, boolean isProto3) {
        indent(sb, indent);

        // Label (optional, required, repeated)
        if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            sb.append("repeated ");
        } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED) {
            sb.append("required ");
        } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL) {
            // In proto2, optional must be explicit
            // In proto3, only add optional if it's explicit (proto3_optional flag)
            if (!isProto3) {
                sb.append("optional ");
            } else if (field.hasProto3Optional() && field.getProto3Optional()) {
                // Proto3 explicit optional (protobuf 3.15+)
                sb.append("optional ");
            }
        }

        appendFieldType(sb, field);
        sb.append(" ").append(field.getName()).append(" = ").append(field.getNumber());
        appendFieldOptions(sb, field);
        sb.append(";\n");
    }

    /**
     * Convert a field that appears within a oneof block.
     * Fields within oneof should not have label keywords (optional/required/repeated).
     */
    private static void convertFieldWithinOneof(StringBuilder sb, DescriptorProtos.FieldDescriptorProto field, int indent, boolean isProto3) {
        indent(sb, indent);
        appendFieldType(sb, field);
        sb.append(" ").append(field.getName()).append(" = ").append(field.getNumber());
        appendFieldOptions(sb, field);
        sb.append(";\n");
    }

    private static void appendFieldType(StringBuilder sb, DescriptorProtos.FieldDescriptorProto field) {
        // Type
        if (field.hasTypeName()) {
            // Message or enum type - remove leading dot
            String typeName = field.getTypeName();
            if (typeName.startsWith(".")) {
                typeName = typeName.substring(1);
            }
            sb.append(typeName);
        } else {
            // Primitive type
            sb.append(getTypeName(field.getType()));
        }
    }

    private static void appendFieldOptions(StringBuilder sb, DescriptorProtos.FieldDescriptorProto field) {
        java.util.List<String> options = new java.util.ArrayList<>();

        // json_name option
        if (field.hasJsonName() && !field.getJsonName().isEmpty()) {
            String defaultJsonName = toJsonName(field.getName());
            if (!defaultJsonName.equals(field.getJsonName())) {
                options.add("json_name = \"" + field.getJsonName() + "\"");
            }
        }

        // Default value (proto2)
        if (field.hasDefaultValue() && !field.getDefaultValue().isEmpty()) {
            String defaultValue = field.getDefaultValue();
            // Quote string defaults
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING ||
                field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES) {
                options.add("default = \"" + escapeString(defaultValue) + "\"");
            } else {
                options.add("default = " + defaultValue);
            }
        }

        // Field options
        if (field.hasOptions()) {
            DescriptorProtos.FieldOptions fieldOptions = field.getOptions();

            if (fieldOptions.hasPacked()) {
                options.add("packed = " + fieldOptions.getPacked());
            }

            if (fieldOptions.hasDeprecated() && fieldOptions.getDeprecated()) {
                options.add("deprecated = true");
            }

            if (fieldOptions.hasLazy() && fieldOptions.getLazy()) {
                options.add("lazy = true");
            }

            if (fieldOptions.hasWeak() && fieldOptions.getWeak()) {
                options.add("weak = true");
            }

            // Note: Custom options would require parsing UnknownFields
            // For now, we skip them as they're complex to handle
        }

        if (!options.isEmpty()) {
            sb.append(" [").append(String.join(", ", options)).append("]");
        }
    }

    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private static void convertEnum(StringBuilder sb, DescriptorProtos.EnumDescriptorProto enumType, int indent) {
        indent(sb, indent);
        sb.append("enum ").append(enumType.getName()).append(" {\n");

        // Enum options
        if (enumType.hasOptions()) {
            DescriptorProtos.EnumOptions enumOptions = enumType.getOptions();
            if (enumOptions.hasAllowAlias() && enumOptions.getAllowAlias()) {
                indent(sb, indent + 1);
                sb.append("option allow_alias = true;\n");
            }
            if (enumOptions.hasDeprecated() && enumOptions.getDeprecated()) {
                indent(sb, indent + 1);
                sb.append("option deprecated = true;\n");
            }
        }

        // Enum values
        for (DescriptorProtos.EnumValueDescriptorProto value : enumType.getValueList()) {
            indent(sb, indent + 1);
            sb.append(value.getName()).append(" = ").append(value.getNumber());

            // Enum value options
            if (value.hasOptions()) {
                DescriptorProtos.EnumValueOptions valueOptions = value.getOptions();
                java.util.List<String> options = new java.util.ArrayList<>();

                if (valueOptions.hasDeprecated() && valueOptions.getDeprecated()) {
                    options.add("deprecated = true");
                }

                if (!options.isEmpty()) {
                    sb.append(" [").append(String.join(", ", options)).append("]");
                }
            }

            sb.append(";\n");
        }

        indent(sb, indent);
        sb.append("}\n");
    }

    private static void convertService(StringBuilder sb, DescriptorProtos.ServiceDescriptorProto service) {
        sb.append("service ").append(service.getName()).append(" {\n");

        // Service options
        if (service.hasOptions()) {
            DescriptorProtos.ServiceOptions serviceOptions = service.getOptions();
            if (serviceOptions.hasDeprecated() && serviceOptions.getDeprecated()) {
                sb.append("  option deprecated = true;\n");
            }
        }

        // Methods
        for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
            sb.append("  rpc ").append(method.getName()).append("(");
            if (method.getClientStreaming()) {
                sb.append("stream ");
            }
            sb.append(stripLeadingDot(method.getInputType())).append(") returns (");
            if (method.getServerStreaming()) {
                sb.append("stream ");
            }
            sb.append(stripLeadingDot(method.getOutputType())).append(")");

            // Method options
            if (method.hasOptions()) {
                DescriptorProtos.MethodOptions methodOptions = method.getOptions();
                java.util.List<String> options = new java.util.ArrayList<>();

                if (methodOptions.hasDeprecated() && methodOptions.getDeprecated()) {
                    options.add("deprecated = true");
                }
                if (methodOptions.hasIdempotencyLevel()) {
                    options.add("idempotency_level = " + methodOptions.getIdempotencyLevel().name());
                }

                if (!options.isEmpty()) {
                    sb.append(" {\n");
                    for (String option : options) {
                        sb.append("    option ").append(option).append(";\n");
                    }
                    sb.append("  }");
                } else {
                    sb.append(";");
                }
            } else {
                sb.append(";");
            }

            sb.append("\n");
        }

        sb.append("}\n");
    }

    private static String getTypeName(DescriptorProtos.FieldDescriptorProto.Type type) {
        switch (type) {
            case TYPE_DOUBLE:
                return "double";
            case TYPE_FLOAT:
                return "float";
            case TYPE_INT32:
                return "int32";
            case TYPE_INT64:
                return "int64";
            case TYPE_UINT32:
                return "uint32";
            case TYPE_UINT64:
                return "uint64";
            case TYPE_SINT32:
                return "sint32";
            case TYPE_SINT64:
                return "sint64";
            case TYPE_FIXED32:
                return "fixed32";
            case TYPE_FIXED64:
                return "fixed64";
            case TYPE_SFIXED32:
                return "sfixed32";
            case TYPE_SFIXED64:
                return "sfixed64";
            case TYPE_BOOL:
                return "bool";
            case TYPE_STRING:
                return "string";
            case TYPE_BYTES:
                return "bytes";
            default:
                return "unknown";
        }
    }

    private static String stripLeadingDot(String typeName) {
        return typeName.startsWith(".") ? typeName.substring(1) : typeName;
    }

    private static String toJsonName(String fieldName) {
        // Convert snake_case to camelCase (protobuf default json_name behavior)
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : fieldName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }
}
