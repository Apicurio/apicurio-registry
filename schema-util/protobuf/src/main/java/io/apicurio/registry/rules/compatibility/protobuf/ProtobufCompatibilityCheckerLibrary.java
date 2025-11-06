package io.apicurio.registry.rules.compatibility.protobuf;

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides compatibility validation functions for changes between two versions of a Protobuf schema document.
 *
 * @see <a href="https://github.com/nilslice/protolock">Protolock</a>
 */
public class ProtobufCompatibilityCheckerLibrary {
    // TODO https://github.com/square/wire/issues/797 RFE: capture EnumElement reserved info

    private final ProtobufFile fileBefore;
    private final ProtobufFile fileAfter;

    public ProtobufCompatibilityCheckerLibrary(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        this.fileBefore = fileBefore;
        this.fileAfter = fileAfter;
    }

    public boolean validate() {
        return findDifferences().isEmpty();
    }

    /**
     * Determine if any new fields have been added, which is forward-compatible.
     *
     * @return differences list (empty for forward-compatible additions)
     */
    public List<ProtobufDifference> checkAddingNewFields() {
        // Adding new fields is forward-compatible, so this method will not add issues.
        return Collections.emptyList();
    }

    public List<ProtobufDifference> findDifferences() {
        return findDifferences(true);
    }

    /**
     * Find differences between two protobuf schemas.
     * @param includeNoUsingReservedFieldsCheck whether to check for removed reserved fields.
     *        Set to false e.g. when comparing registry schemas against compiled protobuf classes,
     *        since the protobuf compiler strips reserved field information from generated code.
     */
    public List<ProtobufDifference> findDifferences(boolean includeNoUsingReservedFieldsCheck) {
        List<ProtobufDifference> totalIssues = new ArrayList<>();
        totalIssues.addAll(checkNoUsingReservedFields());
        if (includeNoUsingReservedFieldsCheck) {
            totalIssues.addAll(checkNoRemovingReservedFields());
        }
        totalIssues.addAll(checkNoRemovingFieldsWithoutReserve());
        totalIssues.addAll(checkNoChangingFieldIDs());
        totalIssues.addAll(checkNoChangingFieldTypes());
        totalIssues.addAll(checkNoChangingFieldNames());
        totalIssues.addAll(checkNoRemovingServiceRPCs());
        totalIssues.addAll(checkNoChangingRPCSignature());
        if (Syntax.PROTO_2.equals(fileBefore.getSyntax())) {
            totalIssues.addAll(checkRequiredFields());
        }
        // Adding the new check for forward-compatible additions
        totalIssues.addAll(checkAddingNewFields());
        return totalIssues;
    }

    /**
     * Determine if any message's previously reserved fields or IDs are now being used as part of the same
     * message.
     * <p>
     * Note: TODO can't currently validate enum reserved fields, as the parser doesn't capture those.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoUsingReservedFields() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Set<Object>> reservedFields = fileBefore.getReservedFields();
        Map<String, Set<Object>> nonReservedFields = fileAfter.getNonReservedFields();

        for (Map.Entry<String, Set<Object>> entry : nonReservedFields.entrySet()) {
            Set<Object> old = reservedFields.get(entry.getKey());
            if (old != null) {
                Set<Object> intersection = new HashSet<>(entry.getValue());
                intersection.retainAll(old);
                if (!intersection.isEmpty()) {
                    issues.add(ProtobufDifference
                            .from(String.format("Conflict of reserved %d fields, message %s",
                                    intersection.size(), entry.getKey())));
                }
            }
        }

        return issues;
    }

    /**
     * Determine if any reserved field has been removed.
     * <p>
     * Note: TODO can't currently validate enum reserved fields, as the parser doesn't capture those.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoRemovingReservedFields() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Set<Object>> before = fileBefore.getReservedFields();
        Map<String, Set<Object>> after = fileAfter.getReservedFields();

        for (Map.Entry<String, Set<Object>> entry : before.entrySet()) {
            Set<Object> afterKeys = after.get(entry.getKey());

            if (afterKeys != null) {
                Set<Object> intersection = new HashSet<>(entry.getValue());
                intersection.retainAll(afterKeys);

                int diff = entry.getValue().size() - intersection.size();
                if (diff != 0) {
                    issues.add(ProtobufDifference.from(String
                            .format("%d reserved fields were removed, message %s", diff, entry.getKey())));
                }
            } else {
                issues.add(
                        ProtobufDifference.from(String.format("%d reserved fields were removed, message %s",
                                entry.getValue().size(), entry.getKey())));
            }
        }

        return issues;
    }

    /**
     * Determine if any field has been removed without a corresponding reservation of that field name or ID.
     * <p>
     * Note: TODO can't currently validate enum reserved fields, as the parser doesn't capture those.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoRemovingFieldsWithoutReserve() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Map<String, FieldElement>> before = fileBefore.getFieldMap();
        Map<String, Map<String, FieldElement>> after = fileAfter.getFieldMap();

        Map<String, Set<Object>> afterReservedFields = fileAfter.getReservedFields();
        Map<String, Set<Object>> afterNonreservedFields = fileAfter.getNonReservedFields();

        for (Map.Entry<String, Map<String, FieldElement>> entry : before.entrySet()) {
            Set<String> removedFieldNames = new HashSet<>(entry.getValue().keySet());
            Map<String, FieldElement> updated = after.get(entry.getKey());
            if (updated != null) {
                removedFieldNames.removeAll(updated.keySet());
            }

            int issuesCount = 0;

            // count once for each non-reserved field name
            Set<Object> reserved = afterReservedFields.getOrDefault(entry.getKey(), Collections.emptySet());
            Set<Object> nonreserved = afterNonreservedFields.getOrDefault(entry.getKey(),
                    Collections.emptySet());
            Set<String> nonReservedRemovedFieldNames = new HashSet<>(removedFieldNames);
            nonReservedRemovedFieldNames.removeAll(reserved);
            issuesCount += nonReservedRemovedFieldNames.size();

            // count again for each non-reserved field id
            for (FieldElement fieldElement : entry.getValue().values()) {
                if (removedFieldNames.contains(fieldElement.getName())
                        && !(reserved.contains(fieldElement.getTag())
                                || nonreserved.contains(fieldElement.getTag()))) {
                    issuesCount++;
                }
            }

            if (issuesCount > 0) {
                issues.add(ProtobufDifference.from(String.format(
                        "%d fields removed without reservation, message %s", issuesCount, entry.getKey())));
            }
        }

        return issues;
    }

    /**
     * Determine if any field ID number has been changed.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoChangingFieldIDs() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Map<String, FieldElement>> before = fileBefore.getFieldMap();
        Map<String, Map<String, FieldElement>> after = fileAfter.getFieldMap();

        for (Map.Entry<String, Map<String, FieldElement>> entry : before.entrySet()) {
            Map<String, FieldElement> afterMap = after.get(entry.getKey());

            if (afterMap != null) {
                for (Map.Entry<String, FieldElement> beforeKV : entry.getValue().entrySet()) {
                    FieldElement afterFE = afterMap.get(beforeKV.getKey());
                    if (afterFE != null && beforeKV.getValue().getTag() != afterFE.getTag()) {
                        issues.add(ProtobufDifference.from(String.format(
                                "Conflict, field id changed, message %s , before: %s , after %s",
                                entry.getKey(), beforeKV.getValue().getTag(), afterFE.getTag())));
                    }
                }
            }
        }

        Map<String, Map<String, EnumConstantElement>> beforeEnum = fileBefore.getEnumFieldMap();
        Map<String, Map<String, EnumConstantElement>> afterEnum = fileAfter.getEnumFieldMap();

        for (Map.Entry<String, Map<String, EnumConstantElement>> entry : beforeEnum.entrySet()) {
            Map<String, EnumConstantElement> afterMap = afterEnum.get(entry.getKey());

            if (afterMap != null) {
                for (Map.Entry<String, EnumConstantElement> beforeKV : entry.getValue().entrySet()) {
                    EnumConstantElement afterECE = afterMap.get(beforeKV.getKey());
                    if (afterECE != null && beforeKV.getValue().getTag() != afterECE.getTag()) {
                        issues.add(ProtobufDifference.from(String.format(
                                "Conflict, field id changed, message %s , before: %s , after %s",
                                entry.getKey(), beforeKV.getValue().getTag(), afterECE.getTag())));
                    }
                }
            }
        }

        return issues;
    }

    /**
     * Determine if any field type has been changed.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoChangingFieldTypes() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Map<String, FieldElement>> before = fileBefore.getFieldMap();
        Map<String, Map<String, FieldElement>> after = fileAfter.getFieldMap();

        for (Map.Entry<String, Map<String, FieldElement>> entry : before.entrySet()) {
            Map<String, FieldElement> afterMap = after.get(entry.getKey());

            if (afterMap != null) {
                for (Map.Entry<String, FieldElement> beforeKV : entry.getValue().entrySet()) {
                    FieldElement afterFE = afterMap.get(beforeKV.getKey());

                    if (afterFE != null) {

                        String beforeType = normalizeType(fileBefore, beforeKV.getValue().getType(),
                                entry.getKey());
                        String afterType = normalizeType(fileAfter, afterFE.getType(), entry.getKey());

                        if (afterFE != null && !beforeType.equals(afterType)) {
                            issues.add(ProtobufDifference.from(String.format(
                                    "Field type changed, message %s , before: %s , after %s", entry.getKey(),
                                    beforeKV.getValue().getType(), afterFE.getType())));
                        }

                        if (afterFE != null
                                && !Objects.equals(beforeKV.getValue().getLabel(), afterFE.getLabel())) {
                            issues.add(ProtobufDifference.from(String.format(
                                    "Field label changed, message %s , before: %s , after %s", entry.getKey(),
                                    beforeKV.getValue().getLabel(), afterFE.getLabel())));
                        }
                    }
                }
            }
        }

        return issues;
    }

    /**
     * Normalizes a protobuf type to its fully qualified form to enable proper comparison
     * between schemas that use different type reference styles (qualified vs unqualified).
     *
     * @param file           the protobuf file containing the type
     * @param type           the type name to normalize
     * @param messageContext the message in which the field is defined (e.g., "RootMessage")
     * @return the normalized fully qualified type name with leading dot (e.g., ".test.RootMessage.NestedMessage")
     */
    private String normalizeType(ProtobufFile file, String type, String messageContext) {
        if (type == null) {
            return null;
        }

        // Handle Protobuf map types
        if (type.startsWith("map<")) {
            return type;
        }

        // If already fully qualified (starts with .), return it as-is
        if (type.startsWith(".")) {
            return type;
        }

        // Handle built-in/primitive types - these don't get qualified
        if (isBuiltInType(type)) {
            return type;
        }

        // Check if this is a cross-package reference (contains dots but doesn't start with one)
        // Examples: google.protobuf.Timestamp, other.package.Message
        // These should just get a leading dot added, not have the local package prepended
        if (type.contains(".")) {
            return "." + type;
        }

        // For non-qualified types, we need to resolve them to fully qualified form
        // 1. Check if it's a nested type in the current message context
        String nestedCandidate = messageContext + "." + type;
        if (typeExistsInFile(file, nestedCandidate)) {
            // It's a nested message/enum in the current message
            return buildFullyQualifiedName(file, nestedCandidate);
        }

        // 2. Check if it's a top-level type in the same file
        if (typeExistsInFile(file, type)) {
            // It's a top-level message/enum in the same file
            return buildFullyQualifiedName(file, type);
        }

        // 3. For types we can't locate in the schema, try to infer the fully qualified name
        // This handles cross-file references. For nested types that we couldn't find,
        // we assume they're nested within the current message context.
        if (messageContext != null && !messageContext.isEmpty()) {
            return buildFullyQualifiedName(file, nestedCandidate);
        }

        // Default: assume it's a top-level type
        return buildFullyQualifiedName(file, type);
    }

    /**
     * Checks if a type (message or enum) exists in the protobuf file.
     *
     * @param file     the protobuf file
     * @param typeName the type name to check (without package, e.g., "Message" or "Outer.Inner")
     * @return true if the type exists as a message or enum
     */
    private boolean typeExistsInFile(ProtobufFile file, String typeName) {
        // Check if it exists as a message (has fields)
        if (file.getFieldMap().containsKey(typeName)) {
            return true;
        }

        // Check if it exists as an enum
        if (file.getEnumFieldMap().containsKey(typeName)) {
            return true;
        }

        // Check if it exists as a message in the non-reserved fields
        // (even if it has no fields, it might be referenced)
        if (file.getNonReservedFields().containsKey(typeName)) {
            return true;
        }

        // Check if it exists as an enum in the non-reserved enum fields
        if (file.getNonReservedEnumFields().containsKey(typeName)) {
            return true;
        }

        return false;
    }

    /**
     * Builds a fully qualified type name with leading dot and package prefix.
     *
     * @param file     the protobuf file
     * @param typePath the type path (e.g., "RootMessage.NestedMessage" or "RootMessage")
     * @return the fully qualified name (e.g., ".test.RootMessage.NestedMessage")
     */
    private String buildFullyQualifiedName(ProtobufFile file, String typePath) {
        String packageName = file.getPackageName();
        if (packageName != null && !packageName.isEmpty()) {
            return "." + packageName + "." + typePath;
        } else {
            return "." + typePath;
        }
    }

    /**
     * Checks if a type is a built-in Protobuf primitive type.
     *
     * @param type the type name to check
     * @return true if the type is a built-in primitive type
     */
    private boolean isBuiltInType(String type) {
        if (type == null) {
            return false;
        }

        // All protobuf primitive/scalar types
        Set<String> builtInTypes = Set.of("double", "float", "int32", "int64", "uint32", "uint64", "sint32",
                "sint64", "fixed32", "fixed64", "sfixed32", "sfixed64", "bool", "string", "bytes");

        return builtInTypes.contains(type);
    }

    /**
     * Determine if any message's previous fields have been renamed.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoChangingFieldNames() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Map<Integer, String>> before = new HashMap<>(fileBefore.getFieldsById());
        before.putAll(fileBefore.getEnumFieldsById());

        Map<String, Map<Integer, String>> after = new HashMap<>(fileAfter.getFieldsById());
        after.putAll(fileAfter.getEnumFieldsById());

        Map<String, Set<Object>> afterReservedFields = fileAfter.getReservedFields();

        for (Map.Entry<String, Map<Integer, String>> entry : before.entrySet()) {
            Map<Integer, String> afterMap = after.get(entry.getKey());

            if (afterMap != null) {
                for (Map.Entry<Integer, String> beforeKV : entry.getValue().entrySet()) {
                    String nameAfter = afterMap.get(beforeKV.getKey());

                    if (!beforeKV.getValue().equals(nameAfter)) {
                        // Check if this is a properly reserved field (removed and reserved)
                        if (nameAfter == null) {
                            Set<Object> reserved = afterReservedFields.getOrDefault(entry.getKey(),
                                    Collections.emptySet());
                            // If the field ID or name is reserved, it's a valid removal, not a rename
                            if (reserved.contains(beforeKV.getKey())
                                    || reserved.contains(beforeKV.getValue())) {
                                continue; // Skip - this is a properly reserved field
                            }
                        }

                        issues.add(ProtobufDifference
                                .from(String.format("Field name changed, message %s , before: %s , after %s",
                                        entry.getKey(), beforeKV.getValue(), nameAfter)));
                    }
                }
            }
        }

        return issues;
    }

    /**
     * Determine if any RPCs provided by a Service have been removed.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoRemovingServiceRPCs() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Set<String>> before = fileBefore.getServiceRPCnames();
        Map<String, Set<String>> after = fileAfter.getServiceRPCnames();

        for (Map.Entry<String, Set<String>> entry : before.entrySet()) {
            Set<String> afterSet = after.get(entry.getKey());

            Set<String> diff = new HashSet<>(entry.getValue());
            if (afterSet != null) {
                diff.removeAll(afterSet);
            }

            if (diff.size() > 0) {
                issues.add(ProtobufDifference.from(
                        String.format("%d rpc services removed, message %s", diff.size(), entry.getKey())));
            }

        }

        return issues;
    }

    /**
     * Determine if any RPC signature has been changed while using the same name.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkNoChangingRPCSignature() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Map<String, String>> before = fileBefore.getServiceRPCSignatures();
        Map<String, Map<String, String>> after = fileAfter.getServiceRPCSignatures();

        for (Map.Entry<String, Map<String, String>> entry : before.entrySet()) {
            Map<String, String> afterMap = after.get(entry.getKey());

            if (afterMap != null) {
                for (Map.Entry<String, String> beforeKV : entry.getValue().entrySet()) {
                    String afterSig = afterMap.get(beforeKV.getKey());
                    if (!beforeKV.getValue().equals(afterSig)) {
                        issues.add(ProtobufDifference.from(String.format(
                                "rpc service signature changed, message %s , before %s , after %s",
                                entry.getKey(), beforeKV.getValue(), afterSig)));

                    }
                }
            }
        }

        return issues;
    }

    /**
     * Determine if any required field has been added in the new version.
     *
     * @return differences list
     */
    public List<ProtobufDifference> checkRequiredFields() {

        List<ProtobufDifference> issues = new ArrayList<>();

        Map<String, Map<String, FieldElement>> before = fileBefore.getFieldMap();
        Map<String, Map<String, FieldElement>> after = fileAfter.getFieldMap();

        for (Map.Entry<String, Map<String, FieldElement>> entry : after.entrySet()) {
            Map<String, FieldElement> beforeMap = before.get(entry.getKey());

            if (beforeMap != null) {
                for (Map.Entry<String, FieldElement> afterKV : entry.getValue().entrySet()) {
                    FieldElement afterSig = beforeMap.get(afterKV.getKey());
                    if (afterSig == null && afterKV.getValue().getLabel() != null
                            && afterKV.getValue().getLabel().equals(Field.Label.REQUIRED)) {
                        issues.add(ProtobufDifference.from(
                                String.format("required field added in new version, message %s, after %s",
                                        entry.getKey(), afterKV.getValue())));
                    }
                }
            }
        }

        return issues;
    }

}
