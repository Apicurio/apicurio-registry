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
        if (!syntax.isEmpty()) {
            sb.append("syntax = \"").append(syntax).append("\";\n");
        }

        sb.append("\n");

        // Package
        if (proto.hasPackage() && !proto.getPackage().isEmpty()) {
            sb.append("package ").append(proto.getPackage()).append(";\n\n");
        }

        // Options
        if (proto.getOptions().hasJavaPackage()) {
            sb.append("option java_package = \"").append(proto.getOptions().getJavaPackage()).append("\";\n");
        }
        if (proto.getOptions().hasJavaOuterClassname()) {
            sb.append("option java_outer_classname = \"").append(proto.getOptions().getJavaOuterClassname()).append("\";\n");
        }
        if (proto.getOptions().hasJavaMultipleFiles()) {
            sb.append("option java_multiple_files = ").append(proto.getOptions().getJavaMultipleFiles()).append(";\n");
        }

        if (proto.getOptions().hasJavaPackage() || proto.getOptions().hasJavaOuterClassname() || proto.getOptions().hasJavaMultipleFiles()) {
            sb.append("\n");
        }

        // Imports
        for (String dependency : proto.getDependencyList()) {
            sb.append("import \"").append(dependency).append("\";\n");
        }
        if (proto.getDependencyCount() > 0) {
            sb.append("\n");
        }

        // Messages
        for (DescriptorProtos.DescriptorProto messageType : proto.getMessageTypeList()) {
            convertMessage(sb, messageType, 0);
            sb.append("\n");
        }

        // Enums
        for (DescriptorProtos.EnumDescriptorProto enumType : proto.getEnumTypeList()) {
            convertEnum(sb, enumType, 0);
            sb.append("\n");
        }

        // Services
        for (DescriptorProtos.ServiceDescriptorProto service : proto.getServiceList()) {
            convertService(sb, service);
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void convertMessage(StringBuilder sb, DescriptorProtos.DescriptorProto message, int indent) {
        indent(sb, indent);
        sb.append("message ").append(message.getName()).append(" {\n");

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

        // Nested enums
        for (DescriptorProtos.EnumDescriptorProto enumType : message.getEnumTypeList()) {
            convertEnum(sb, enumType, indent + 1);
        }

        // Nested messages
        for (DescriptorProtos.DescriptorProto nestedType : message.getNestedTypeList()) {
            // Skip map entry messages
            if (!nestedType.getOptions().getMapEntry()) {
                convertMessage(sb, nestedType, indent + 1);
            }
        }

        // Fields
        for (DescriptorProtos.FieldDescriptorProto field : message.getFieldList()) {
            convertField(sb, field, indent + 1);
        }

        indent(sb, indent);
        sb.append("}\n");
    }

    private static void convertField(StringBuilder sb, DescriptorProtos.FieldDescriptorProto field, int indent) {
        indent(sb, indent);

        // Label (optional, required, repeated)
        if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            sb.append("repeated ");
        } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED) {
            sb.append("required ");
        } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL) {
            // In proto3, optional is implicit; in proto2, it's explicit
            sb.append("optional ");
        }

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

        sb.append(" ").append(field.getName()).append(" = ").append(field.getNumber());

        // Options
        if (field.hasJsonName() && !field.getJsonName().isEmpty()) {
            String defaultJsonName = toJsonName(field.getName());
            if (!defaultJsonName.equals(field.getJsonName())) {
                sb.append(" [json_name = \"").append(field.getJsonName()).append("\"]");
            }
        }

        sb.append(";\n");
    }

    private static void convertEnum(StringBuilder sb, DescriptorProtos.EnumDescriptorProto enumType, int indent) {
        indent(sb, indent);
        sb.append("enum ").append(enumType.getName()).append(" {\n");

        for (DescriptorProtos.EnumValueDescriptorProto value : enumType.getValueList()) {
            indent(sb, indent + 1);
            sb.append(value.getName()).append(" = ").append(value.getNumber()).append(";\n");
        }

        indent(sb, indent);
        sb.append("}\n");
    }

    private static void convertService(StringBuilder sb, DescriptorProtos.ServiceDescriptorProto service) {
        sb.append("service ").append(service.getName()).append(" {\n");

        for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
            sb.append("  rpc ").append(method.getName()).append("(");
            if (method.getClientStreaming()) {
                sb.append("stream ");
            }
            sb.append(stripLeadingDot(method.getInputType())).append(") returns (");
            if (method.getServerStreaming()) {
                sb.append("stream ");
            }
            sb.append(stripLeadingDot(method.getOutputType())).append(");\n");
        }

        sb.append("}\n");
    }

    private static String getTypeName(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_DOUBLE -> "double";
            case TYPE_FLOAT -> "float";
            case TYPE_INT32 -> "int32";
            case TYPE_INT64 -> "int64";
            case TYPE_UINT32 -> "uint32";
            case TYPE_UINT64 -> "uint64";
            case TYPE_SINT32 -> "sint32";
            case TYPE_SINT64 -> "sint64";
            case TYPE_FIXED32 -> "fixed32";
            case TYPE_FIXED64 -> "fixed64";
            case TYPE_SFIXED32 -> "sfixed32";
            case TYPE_SFIXED64 -> "sfixed64";
            case TYPE_BOOL -> "bool";
            case TYPE_STRING -> "string";
            case TYPE_BYTES -> "bytes";
            default -> "unknown";
        };
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
        sb.append("  ".repeat(Math.max(0, level)));
    }
}
