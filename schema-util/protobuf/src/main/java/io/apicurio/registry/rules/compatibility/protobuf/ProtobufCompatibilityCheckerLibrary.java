/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.compatibility.protobuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.FieldElement;

import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

/**
 * Provides compatibility validation functions for changes between two versions of a Protobuf schema document.
 *
 * @author Jonathan Halliday
 * @author Ales Justin
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

    public List<ProtobufDifference> findDifferences() {
        List<ProtobufDifference> totalIssues = new ArrayList<>();
        totalIssues.addAll(checkNoUsingReservedFields());
        totalIssues.addAll(checkNoRemovingReservedFields());
        totalIssues.addAll(checkNoRemovingFieldsWithoutReserve());
        totalIssues.addAll(checkNoChangingFieldIDs());
        totalIssues.addAll(checkNoChangingFieldTypes());
        totalIssues.addAll(checkNoChangingFieldNames());
        totalIssues.addAll(checkNoRemovingServiceRPCs());
        totalIssues.addAll(checkNoChangingRPCSignature());
        if (fileBefore.getSyntax().equals(Syntax.PROTO_2)) {
            totalIssues.addAll(checkRequiredFields());
        }
        return totalIssues;
    }

    /**
     * Determine if any message's previously reserved fields or IDs are now being used as part of the same message.
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
                    issues.add(ProtobufDifference.from(String.format("Conflict of reserved %d fields, message %s", intersection.size(), entry.getKey())));
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
                    issues.add(ProtobufDifference.from(String.format("%d reserved fields were removed, message %s", diff, entry.getKey())));
                }
            } else {
                issues.add(ProtobufDifference.from(String.format("%d reserved fields were removed, message %s", entry.getValue().size(), entry.getKey())));
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
            Set<Object> nonreserved = afterNonreservedFields.getOrDefault(entry.getKey(), Collections.emptySet());
            Set<String> nonReservedRemovedFieldNames = new HashSet<>(removedFieldNames);
            nonReservedRemovedFieldNames.removeAll(reserved);
            issuesCount += nonReservedRemovedFieldNames.size();

            // count again for each non-reserved field id
            for (FieldElement fieldElement : entry.getValue().values()) {
                if (removedFieldNames.contains(fieldElement.getName()) &&
                    !(reserved.contains(fieldElement.getTag()) || nonreserved.contains(fieldElement.getTag()))) {
                    issuesCount++;
                }
            }

            if (issuesCount > 0) {
                issues.add(ProtobufDifference.from(String.format("%d fields removed without reservation, message %s", issuesCount, entry.getKey())));
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
                        issues.add(ProtobufDifference.from(String.format("Conflict, field id changed, message %s , before: %s , after %s", entry.getKey(), beforeKV.getValue().getTag(), afterFE.getTag())));
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
                        issues.add(ProtobufDifference.from(String.format("Conflict, field id changed, message %s , before: %s , after %s", entry.getKey(), beforeKV.getValue().getTag(), afterECE.getTag())));
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

                        String beforeType = normalizeType(fileBefore, beforeKV.getValue().getType());
                        String afterType = normalizeType(fileAfter, afterFE.getType());

                        if (afterFE != null && !beforeType.equals(afterType)) {
                            issues.add(ProtobufDifference.from(String.format("Field type changed, message %s , before: %s , after %s", entry.getKey(), beforeKV.getValue().getType(), afterFE.getType())));
                        }

                        if (afterFE != null && !Objects.equals(beforeKV.getValue().getLabel(), afterFE.getLabel())) {
                            issues.add(ProtobufDifference.from(String.format("Field label changed, message %s , before: %s , after %s", entry.getKey(), beforeKV.getValue().getLabel(), afterFE.getLabel())));
                        }
                    }
                }
            }
        }

        return issues;
    }

    private String normalizeType(ProtobufFile file, String type) {
        if (type != null && type.startsWith(".")) {
            //it's fully qualified
            String nodot = type.substring(1);
            if (file.getPackageName() != null && nodot.startsWith(file.getPackageName())) {
                //it's fully qualified but it's a message in the same .proto file
                return nodot.substring(file.getPackageName().length() + 1);
            }
            return nodot;
        }
        return type;
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

        for (Map.Entry<String, Map<Integer, String>> entry : before.entrySet()) {
            Map<Integer, String> afterMap = after.get(entry.getKey());

            if (afterMap != null) {
                for (Map.Entry<Integer, String> beforeKV : entry.getValue().entrySet()) {
                    String nameAfter = afterMap.get(beforeKV.getKey());

                    if (!beforeKV.getValue().equals(nameAfter)) {
                        issues.add(ProtobufDifference.from(String.format("Field name changed, message %s , before: %s , after %s", entry.getKey(), beforeKV.getValue(), nameAfter)));
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
                issues.add(ProtobufDifference.from(String.format("%d rpc services removed, message %s", diff.size(), entry.getKey())));
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
                        issues.add(ProtobufDifference.from(String.format("rpc service signature changed, message %s , before %s , after %s", entry.getKey(), beforeKV.getValue(), afterSig)));

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
                    if (afterSig == null && afterKV.getValue().getLabel() != null && afterKV.getValue().getLabel().equals(Field.Label.REQUIRED)) {
                        issues.add(ProtobufDifference.from(String.format("required field added in new version, message %s, after %s", entry.getKey(), afterKV.getValue())));
                    }
                }
            }
        }

        return issues;
    }

}
