/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.utils.protobuf.schema;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import com.squareup.wire.schema.internal.parser.ReservedElement;
import com.squareup.wire.schema.internal.parser.RpcElement;
import com.squareup.wire.schema.internal.parser.ServiceElement;
import com.squareup.wire.schema.internal.parser.TypeElement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexed representation of the data resulting from parsing a single .proto protobuf schema file,
 * used mainly for schema validation.
 *
 * @author Jonathan Halliday
 * @author Ales Justin
 * @see <a href="https://github.com/nilslice/protolock">Protolock</a>
 * @see ProtobufCompatibilityChecker
 */
public class ProtobufFile {

    private final ProtoFileElement element;

    private final Map<String, Set<Object>> reservedFields = new HashMap<>();

    private final Map<String, Map<String, FieldElement>> fieldMap = new HashMap<>();
    private final Map<String, Map<String, EnumConstantElement>> enumFieldMap = new HashMap<>();

    private final Map<String, Map<String, FieldElement>> mapMap = new HashMap<>();

    private final Map<String, Set<Object>> nonReservedFields = new HashMap<>();
    private final Map<String, Set<Object>> nonReservedEnumFields = new HashMap<>();

    private final Map<String, Map<Integer, String>> fieldsById = new HashMap<>();
    private final Map<String, Map<Integer, String>> enumFieldsById = new HashMap<>();

    private final Map<String, Set<String>> serviceRPCnames = new HashMap<>();
    private final Map<String, Map<String, String>> serviceRPCSignatures = new HashMap<>();

    public ProtobufFile(String data) {
        element = toProtoFileElement(data);
        buildIndexes();
    }

    public ProtobufFile(File file) throws IOException {
//        Location location = Location.get(file.getAbsolutePath());
        List<String> data = Files.readLines(file, StandardCharsets.UTF_8);
        element = toProtoFileElement(String.join("\n", data));
        buildIndexes();
    }

    public ProtobufFile(ProtoFileElement element) {
        this.element = element;
        buildIndexes();
    }

    public static ProtoFileElement toProtoFileElement(String data) {
        ProtoParser parser = new ProtoParser(Location.get(""), data.toCharArray());
        return parser.readProtoFile();
    }

    public String getPackageName() {
        return element.getPackageName();
    }

    /*
     * message name -> Set { Integer/tag || String/name }
     */
    public Map<String, Set<Object>> getReservedFields() {
        return reservedFields;
    }

    /*
     * message name -> Map { field name -> FieldElement }
     */
    public Map<String, Map<String, FieldElement>> getFieldMap() {
        return fieldMap;
    }

    /*
     * enum name -> Map { String/name -> EnumConstantElement }
     */
    public Map<String, Map<String, EnumConstantElement>> getEnumFieldMap() {
        return enumFieldMap;
    }

    /*
     * message name -> Map { field name -> FieldElement }
     */
    public Map<String, Map<String, FieldElement>> getMapMap() {
        return mapMap;
    }

    /*
     * message name -> Set { Integer/tag || String/name }
     */
    public Map<String, Set<Object>> getNonReservedFields() {
        return nonReservedFields;
    }

    /*
     * enum name -> Set { Integer/tag || String/name }
     */
    public Map<String, Set<Object>> getNonReservedEnumFields() {
        return nonReservedEnumFields;
    }

    /*
     * message name -> Map { field id -> field name }
     */
    public Map<String, Map<Integer, String>> getFieldsById() {
        return fieldsById;
    }

    /*
     * enum name -> Map { field id -> field name }
     */
    public Map<String, Map<Integer, String>> getEnumFieldsById() {
        return enumFieldsById;
    }

    /*
     * service name -> Set { rpc name }
     */
    public Map<String, Set<String>> getServiceRPCnames() {
        return serviceRPCnames;
    }

    /*
     * service name -> Map { rpc name -> method signature }
     */
    public Map<String, Map<String, String>> getServiceRPCSignatures() {
        return serviceRPCSignatures;
    }

    public Syntax getSyntax() {
        return element.getSyntax();
    }

    private void buildIndexes() {

        for (TypeElement typeElement : element.getTypes()) {
            if (typeElement instanceof MessageElement) {

                MessageElement messageElement = (MessageElement) typeElement;
                processMessageElement("", messageElement);

            } else if (typeElement instanceof EnumElement) {

                EnumElement enumElement = (EnumElement) typeElement;
                processEnumElement("", enumElement);

            } else {
                throw new RuntimeException();
            }
        }

        for (ServiceElement serviceElement : element.getServices()) {
            Set<String> rpcNames = new HashSet<>();
            Map<String, String> rpcSignatures = new HashMap<>();
            for (RpcElement rpcElement : serviceElement.getRpcs()) {
                rpcNames.add(rpcElement.getName());

                String signature = rpcElement.getRequestType() + ":" + rpcElement.getRequestStreaming() + "->" + rpcElement.getResponseType() + ":" + rpcElement.getResponseStreaming();
                rpcSignatures.put(rpcElement.getName(), signature);
            }
            if (!rpcNames.isEmpty()) {
                serviceRPCnames.put(serviceElement.getName(), rpcNames);
                serviceRPCSignatures.put(serviceElement.getName(), rpcSignatures);
            }

        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void processMessageElement(String scope, MessageElement messageElement) {

        // reservedFields
        Set<Object> reservedFieldSet = new HashSet<>();
        for (ReservedElement reservedElement : messageElement.getReserveds()) {
            for (Object value : reservedElement.getValues()) {
                if (value instanceof Range) {
                    reservedFieldSet.addAll(ContiguousSet.create((Range) value, DiscreteDomain.integers()));
                } else {
                    reservedFieldSet.add(value);
                }
            }
        }
        if (!reservedFieldSet.isEmpty()) {
            reservedFields.put(scope + messageElement.getName(), reservedFieldSet);
        }

        // fieldMap, mapMap, FieldsIDName
        Map<String, FieldElement> fieldTypeMap = new HashMap<>();
        Map<String, FieldElement> mapMap = new HashMap<>();
        Map<Integer, String> idsToNames = new HashMap<>();
        for (FieldElement fieldElement : messageElement.getFields()) {
            fieldTypeMap.put(fieldElement.getName(), fieldElement);
            if (fieldElement.getType().startsWith("map<")) {
                mapMap.put(fieldElement.getName(), fieldElement);
            }
            idsToNames.put(fieldElement.getTag(), fieldElement.getName());
        }
        for (OneOfElement oneOfElement : messageElement.getOneOfs()) {
            for (FieldElement fieldElement : oneOfElement.getFields()) {
                fieldTypeMap.put(fieldElement.getName(), fieldElement);
                if (fieldElement.getType().startsWith("map<")) {
                    mapMap.put(fieldElement.getName(), fieldElement);
                }
                idsToNames.put(fieldElement.getTag(), fieldElement.getName());
            }
        }

        if (!fieldTypeMap.isEmpty()) {
            fieldMap.put(scope + messageElement.getName(), fieldTypeMap);
        }
        if (!mapMap.isEmpty()) {
            this.mapMap.put(scope + messageElement.getName(), mapMap);
        }
        if (!idsToNames.isEmpty()) {
            fieldsById.put(scope + messageElement.getName(), idsToNames);
        }

        // nonReservedFields
        Set<Object> fieldKeySet = new HashSet<>();
        for (FieldElement fieldElement : messageElement.getFields()) {
            fieldKeySet.add(fieldElement.getTag());
            fieldKeySet.add(fieldElement.getName());
        }
        for (OneOfElement oneOfElement : messageElement.getOneOfs()) {
            for (FieldElement fieldElement : oneOfElement.getFields()) {
                fieldKeySet.add(fieldElement.getTag());
                fieldKeySet.add(fieldElement.getName());
            }
        }


        if (!fieldKeySet.isEmpty()) {
            nonReservedFields.put(scope + messageElement.getName(), fieldKeySet);
        }

        for (TypeElement typeElement : messageElement.getNestedTypes()) {
            if (typeElement instanceof MessageElement) {
                processMessageElement(messageElement.getName() + ".", (MessageElement) typeElement);
            } else if (typeElement instanceof EnumElement) {
                processEnumElement(messageElement.getName() + ".", (EnumElement) typeElement);
            }
        }
    }

    private void processEnumElement(String scope, EnumElement enumElement) {

        // TODO reservedEnumFields - wire doesn't preserve these
        // https://github.com/square/wire/issues/797 RFE: capture EnumElement reserved info

        // enumFieldMap, enumFieldsIDName, nonReservedEnumFields
        Map<String, EnumConstantElement> map = new HashMap<>();
        Map<Integer, String> idsToNames = new HashMap<>();
        Set<Object> fieldKeySet = new HashSet<>();
        for (EnumConstantElement enumConstantElement : enumElement.getConstants()) {
            map.put(enumConstantElement.getName(), enumConstantElement);
            idsToNames.put(enumConstantElement.getTag(), enumConstantElement.getName());

            fieldKeySet.add(enumConstantElement.getTag());
            fieldKeySet.add(enumConstantElement.getName());
        }
        if (!map.isEmpty()) {
            enumFieldMap.put(scope + enumElement.getName(), map);
        }
        if (!idsToNames.isEmpty()) {
            enumFieldsById.put(scope + enumElement.getName(), idsToNames);
        }
        if (!fieldKeySet.isEmpty()) {
            nonReservedEnumFields.put(scope + enumElement.getName(), fieldKeySet);
        }
    }
}
