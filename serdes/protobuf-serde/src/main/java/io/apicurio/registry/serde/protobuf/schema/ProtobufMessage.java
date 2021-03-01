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

package io.apicurio.registry.serde.protobuf.schema;

import java.util.HashMap;
import java.util.Map;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;

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
            String name,
            int num,
            String defaultVal,
            String jsonName,
            Boolean isPacked,
            Integer oneOfIndex
        ) {
        FieldDescriptorProto.Label protoLabel = fieldDescriptorLabels.get(label);
        addFieldDescriptorProto(protoLabel, type, name, num, defaultVal, jsonName, isPacked, oneOfIndex);
    }

    public void addFieldDescriptorProto(
            FieldDescriptorProto.Label label,
            String type,
            String name,
            int num,
            String defaultVal,
            String jsonName,
            Boolean isPacked,
            Integer oneOfIndex
        ) {

        FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder();
        if (label != null) {
            fieldBuilder.setLabel(label);
        }
        FieldDescriptorProto.Type primType = fieldDescriptorTypes.get(type);
        if (primType != null) {
            fieldBuilder.setType(primType);
        } else {
            fieldBuilder.setTypeName(type);
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
        if (isPacked != null) {
            DescriptorProtos.FieldOptions.Builder optionsBuilder = DescriptorProtos.FieldOptions.newBuilder();
            optionsBuilder.setPacked(isPacked);
            fieldBuilder.mergeOptions(optionsBuilder.build());
        }
        descriptorProtoBuilder.addField(fieldBuilder.build());
    }

}
