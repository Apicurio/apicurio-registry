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

package io.apicurio.registry.utils.protobuf.schema;

import java.util.HashMap;
import java.util.Map;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import metadata.ProtobufSchemaMetadata;

/**
 * @author Fabian Martinez
 */
public class ProtobufMessage {

    private static Map<String, FieldDescriptorProto.Type> fieldDescriptorTypes;
    private static Map<String, FieldDescriptorProto.Label> fieldDescriptorLabels;

    static {
        fieldDescriptorLabels = new HashMap<String, FieldDescriptorProto.Label>();
        fieldDescriptorLabels.put("optional", FieldDescriptorProto.Label.LABEL_OPTIONAL);
        fieldDescriptorLabels.put("required", FieldDescriptorProto.Label.LABEL_REQUIRED);
        fieldDescriptorLabels.put("repeated", FieldDescriptorProto.Label.LABEL_REPEATED);

        fieldDescriptorTypes = new HashMap<String, FieldDescriptorProto.Type>();
        fieldDescriptorTypes.put("double", FieldDescriptorProto.Type.TYPE_DOUBLE);
        fieldDescriptorTypes.put("float", FieldDescriptorProto.Type.TYPE_FLOAT);
        fieldDescriptorTypes.put("int32", FieldDescriptorProto.Type.TYPE_INT32);
        fieldDescriptorTypes.put("int64", FieldDescriptorProto.Type.TYPE_INT64);
        fieldDescriptorTypes.put("uint32", FieldDescriptorProto.Type.TYPE_UINT32);
        fieldDescriptorTypes.put("uint64", FieldDescriptorProto.Type.TYPE_UINT64);
        fieldDescriptorTypes.put("sint32", FieldDescriptorProto.Type.TYPE_SINT32);
        fieldDescriptorTypes.put("sint64", FieldDescriptorProto.Type.TYPE_SINT64);
        fieldDescriptorTypes.put("fixed32", FieldDescriptorProto.Type.TYPE_FIXED32);
        fieldDescriptorTypes.put("fixed64", FieldDescriptorProto.Type.TYPE_FIXED64);
        fieldDescriptorTypes.put("sfixed32", FieldDescriptorProto.Type.TYPE_SFIXED32);
        fieldDescriptorTypes.put("sfixed64", FieldDescriptorProto.Type.TYPE_SFIXED64);
        fieldDescriptorTypes.put("bool", FieldDescriptorProto.Type.TYPE_BOOL);
        fieldDescriptorTypes.put("string", FieldDescriptorProto.Type.TYPE_STRING);
        fieldDescriptorTypes.put("bytes", FieldDescriptorProto.Type.TYPE_BYTES);
        fieldDescriptorTypes.put("enum", FieldDescriptorProto.Type.TYPE_ENUM);
        fieldDescriptorTypes.put("message", FieldDescriptorProto.Type.TYPE_MESSAGE);
        fieldDescriptorTypes.put("group", FieldDescriptorProto.Type.TYPE_GROUP);
    }

    private DescriptorProto.Builder descriptorProtoBuilder;

    public ProtobufMessage() {
        this.descriptorProtoBuilder = DescriptorProto.newBuilder();
    }

    public DescriptorProto.Builder protoBuilder() {
        return descriptorProtoBuilder;
    }

    public DescriptorProto build() {
        return descriptorProtoBuilder.build();
    }

    public void addField(
            String label,
            String type,
            String typeName,
            String name,
            int num,
            String defaultVal,
            String jsonName,
            Boolean isDeprecated,
            Boolean isPacked,
            DescriptorProtos.FieldOptions.CType ctype,
            DescriptorProtos.FieldOptions.JSType jsType,
            String metadataKey,
            String metadataValue,
            Integer oneOfIndex,
            Boolean isProto3Optional
        ) {
        descriptorProtoBuilder.addField(
                buildFieldDescriptorProto(label, type, typeName, name, num, defaultVal, jsonName, isDeprecated,
                        isPacked, ctype, jsType, metadataKey, metadataValue, oneOfIndex, isProto3Optional)
        );
    }

    public static FieldDescriptorProto buildFieldDescriptorProto(String label,
                                                                 String type,
                                                                 String typeName,
                                                                 String name,
                                                                 int num,
                                                                 String defaultVal,
                                                                 String jsonName,
                                                                 Boolean isDeprecated,
                                                                 Boolean isPacked,
                                                                 DescriptorProtos.FieldOptions.CType ctype,
                                                                 DescriptorProtos.FieldOptions.JSType jsType,
                                                                 String metadataKey,
                                                                 String metadataValue,
                                                                 Integer oneOfIndex,
                                                                 Boolean isProto3Optional) {
        FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder();
        FieldDescriptorProto.Label protoLabel = fieldDescriptorLabels.get(label);
        if (label != null) {
            fieldBuilder.setLabel(protoLabel);
        }
        FieldDescriptorProto.Type primType = fieldDescriptorTypes.get(typeName);
        if (primType != null) {
            fieldBuilder.setType(primType);
        } else {
            FieldDescriptorProto.Type fieldDescriptorType = null;
            if (type != null) {
                fieldDescriptorType = fieldDescriptorTypes.get(type);
                fieldBuilder.setType(fieldDescriptorType);
            }
            if (fieldDescriptorType != null &&
                    (fieldDescriptorType.equals(FieldDescriptorProto.Type.TYPE_MESSAGE) || fieldDescriptorType.equals(
                            FieldDescriptorProto.Type.TYPE_ENUM)))  {
                //References to other nested messages / enums / google.protobuf types start with "."
                //See https://developers.google.com/protocol-buffers/docs/proto#packages_and_name_resolution
                fieldBuilder.setTypeName(typeName.startsWith(".") ? typeName : "." + typeName);
            } else {
                fieldBuilder.setTypeName(typeName);
            }
        }
        fieldBuilder.setName(name).setNumber(num);
        if (defaultVal != null) {
            fieldBuilder.setDefaultValue(defaultVal);
        }
        if (oneOfIndex != null) {
            fieldBuilder.setOneofIndex(oneOfIndex);
        }
        if (jsonName != null) {
            fieldBuilder.setJsonName(jsonName);
        }

        if (isDeprecated != null) {
            DescriptorProtos.FieldOptions.Builder optionsBuilder = DescriptorProtos.FieldOptions.newBuilder();
            optionsBuilder.setDeprecated(isDeprecated);
            fieldBuilder.mergeOptions(optionsBuilder.build());
        }

        if (isPacked != null) {
            DescriptorProtos.FieldOptions.Builder optionsBuilder = DescriptorProtos.FieldOptions.newBuilder();
            optionsBuilder.setPacked(isPacked);
            fieldBuilder.mergeOptions(optionsBuilder.build());
        }

        if (ctype != null) {
            DescriptorProtos.FieldOptions.Builder optionsBuilder = DescriptorProtos.FieldOptions.newBuilder();
            optionsBuilder.setCtype(ctype);
            fieldBuilder.mergeOptions(optionsBuilder.build());
        }

        if (metadataKey != null) {
            DescriptorProtos.FieldOptions.Builder optionsBuilder = DescriptorProtos.FieldOptions.newBuilder();
            optionsBuilder.setExtension(ProtobufSchemaMetadata.metadataKey, metadataKey);
            fieldBuilder.mergeOptions(optionsBuilder.build());
        }

        if (metadataValue != null) {
            DescriptorProtos.FieldOptions.Builder optionsBuilder = DescriptorProtos.FieldOptions.newBuilder();
            optionsBuilder.setExtension(ProtobufSchemaMetadata.metadataValue, metadataValue);
            fieldBuilder.mergeOptions(optionsBuilder.build());
        }

        if (jsType != null) {
            DescriptorProtos.FieldOptions.Builder optionsBuilder = DescriptorProtos.FieldOptions.newBuilder();
            optionsBuilder.setJstype(jsType);
            fieldBuilder.mergeOptions(optionsBuilder.build());
        }

        if (isProto3Optional != null) {
            fieldBuilder.setProto3Optional(isProto3Optional);
        }
        return fieldBuilder.build();
    }

}
