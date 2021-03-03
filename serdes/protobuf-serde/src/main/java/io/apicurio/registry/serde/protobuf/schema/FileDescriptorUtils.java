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

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.WrappersProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ReservedElement;
import com.squareup.wire.schema.internal.parser.TypeElement;

import kotlin.ranges.IntRange;

/**
 * @author Fabian Martinez
 */
public class FileDescriptorUtils {

    public static final Location DEFAULT_LOCATION = Location.get("");

    private static final String PROTO2 = "proto2";
    private static final String PROTO3 = "proto3";
    private static final String ALLOW_ALIAS_OPTION = "allow_alias";
    private static final String MAP_ENTRY_OPTION = "map_entry";
    private static final String PACKED_OPTION = "packed";
    private static final String JSON_NAME_OPTION = "json_name";
    private static final String JAVA_MULTIPLE_FILES_OPTION = "java_multiple_files";
    private static final String JAVA_OUTER_CLASSNAME_OPTION = "java_outer_classname";
    private static final String JAVA_PACKAGE_OPTION = "java_package";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static final String MAP_ENTRY_SUFFIX = "Entry";

    public static FileDescriptor[] baseDependencies() {
        return new FileDescriptor[] {
                    TimestampProto.getDescriptor().getFile(),
                    WrappersProto.getDescriptor().getFile()
                };
    }

    public static FileDescriptor protoFileToFileDescriptor(ProtoFileElement element) throws DescriptorValidationException {
        return FileDescriptor.buildFrom(toFileDescriptorProto(element), baseDependencies());
    }

    private static FileDescriptorProto toFileDescriptorProto(ProtoFileElement element) {
        FileDescriptorProto.Builder schema = FileDescriptorProto.newBuilder();
        schema.setName("default");

        ProtoFile.Syntax syntax = element.getSyntax();
        if (syntax != null) {
            schema.setSyntax(syntax.toString());
        }
        if (element.getPackageName() != null) {
            schema.setPackage(element.getPackageName());
        }

        for (TypeElement typeElem : element.getTypes()) {
            if (typeElem instanceof MessageElement) {
                DescriptorProto message = messageElementToDescriptorProto((MessageElement) typeElem);
                schema.addMessageType(message);
            } else if (typeElem instanceof EnumElement) {
                EnumDescriptorProto enumer = enumElementToProto((EnumElement) typeElem);
                schema.addEnumType(enumer);
            }
        }

        //dependencies on protobuf default types are always added
        for (String ref : element.getImports()) {
            schema.addDependency(ref);
        }
        for (String ref : element.getPublicImports()) {
            boolean add = true;
            for (int i = 0; i < schema.getDependencyCount(); i++) {
                if (schema.getDependency(i).equals(ref)) {
                    schema.addPublicDependency(i);
                    add = false;
                }
            }
            if (add) {
                schema.addDependency(ref);
                schema.addPublicDependency(schema.getDependencyCount() - 1);
            }
        }

        String javaPackageName = findOptionString(JAVA_PACKAGE_OPTION, element.getOptions());
        if (javaPackageName != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder()
                    .setJavaPackage(javaPackageName)
                    .build();
            schema.mergeOptions(options);
        }

        String javaOuterClassname = findOptionString(JAVA_OUTER_CLASSNAME_OPTION, element.getOptions());
        if (javaOuterClassname != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder()
                    .setJavaOuterClassname(javaOuterClassname)
                    .build();
            schema.mergeOptions(options);
        }

        Boolean javaMultipleFiles = findOptionBoolean(JAVA_MULTIPLE_FILES_OPTION, element.getOptions());
        if (javaMultipleFiles != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder()
                    .setJavaMultipleFiles(javaMultipleFiles)
                    .build();
            schema.mergeOptions(options);
        }

        return schema.build();
    }

    private static DescriptorProto messageElementToDescriptorProto(MessageElement messageElem) {
        ProtobufMessage message = new ProtobufMessage();
        message.protoBuilder().setName(messageElem.getName());

        for (TypeElement type : messageElem.getNestedTypes()) {
            if (type instanceof MessageElement) {
                message.protoBuilder().addNestedType(messageElementToDescriptorProto((MessageElement) type));
            } else if (type instanceof EnumElement) {
                message.protoBuilder().addEnumType(enumElementToProto((EnumElement) type));
            }
        }

        Set<String> added = new HashSet<>();

        for (OneOfElement oneof : messageElem.getOneOfs()) {
            OneofDescriptorProto.Builder oneofBuilder = OneofDescriptorProto.newBuilder().setName(oneof.getName());
            message.protoBuilder().addOneofDecl(oneofBuilder);

            for (FieldElement field : oneof.getFields()) {
                String jsonName = findOptionString(JSON_NAME_OPTION, field.getOptions());

                message.addFieldDescriptorProto(
                        FieldDescriptorProto.Label.LABEL_OPTIONAL,
                        field.getType(),
                        field.getName(),
                        field.getTag(),
                        field.getDefaultValue(),
                        jsonName,
                        null,
                        message.protoBuilder().getOneofDeclCount() - 1);

                added.add(field.getName());
            }
        }

        // Process fields after messages so that any newly created map entry messages are at the end
        for (FieldElement field : messageElem.getFields()) {
            if (added.contains(field.getName())) {
                continue;
            }
            Field.Label fieldLabel = field.getLabel();
            String label = fieldLabel != null ? fieldLabel.toString().toLowerCase() : null;
            String fieldType = field.getType();

            ProtoType protoType = ProtoType.get(fieldType);
            ProtoType keyType = protoType.getKeyType();
            ProtoType valueType = protoType.getValueType();
            // Map fields are only permitted in messages
            if (protoType.isMap() && keyType != null && valueType != null) {
                label = "repeated";
                fieldType = toMapEntry(field.getName());
                ProtobufMessage protobufMapMessage = new ProtobufMessage();
                DescriptorProto.Builder mapMessage = protobufMapMessage
                        .protoBuilder()
                        .setName(fieldType)
                        .mergeOptions(DescriptorProtos.MessageOptions.newBuilder()
                                .setMapEntry(true)
                                .build());

                protobufMapMessage.addField(null, keyType.getSimpleName(), KEY_FIELD, 1, null, null, null, null);
                protobufMapMessage.addField(null, valueType.getSimpleName(), VALUE_FIELD, 2, null, null, null, null);
                message.protoBuilder().addNestedType(mapMessage.build());
            }

            String jsonName = findOptionString(JSON_NAME_OPTION, field.getOptions());
            Boolean isPacked = findOptionBoolean(PACKED_OPTION, field.getOptions());

            message.addField(label, fieldType, field.getName(), field.getTag(), field.getDefaultValue(), jsonName, isPacked, null);
        }

        for (ReservedElement reserved : messageElem.getReserveds()) {
            for (Object elem : reserved.getValues()) {
                if (elem instanceof String) {
                    message.protoBuilder().addReservedName((String) elem);
                } else if (elem instanceof Integer) {
                    int tag = (Integer) elem;
                    DescriptorProto.ReservedRange.Builder rangeBuilder = DescriptorProto.ReservedRange.newBuilder()
                            .setStart(tag)
                            .setEnd(tag);
                    message.protoBuilder().addReservedRange(rangeBuilder.build());
                } else if (elem instanceof IntRange) {
                    IntRange range = (IntRange) elem;
                    DescriptorProto.ReservedRange.Builder rangeBuilder = DescriptorProto.ReservedRange.newBuilder()
                            .setStart(range.getStart())
                            .setEnd(range.getEndInclusive());
                    message.protoBuilder().addReservedRange(rangeBuilder.build());
                } else {
                    throw new IllegalStateException(
                            "Unsupported reserved type: " + elem.getClass().getName());
                }
            }
        }
        Boolean isMapEntry = findOptionBoolean(MAP_ENTRY_OPTION, messageElem.getOptions());
        if (isMapEntry != null) {
            DescriptorProtos.MessageOptions.Builder optionsBuilder = DescriptorProtos.MessageOptions.newBuilder()
                    .setMapEntry(isMapEntry);
            message.protoBuilder().mergeOptions(optionsBuilder.build());
        }
        return message.build();
    }

    private static EnumDescriptorProto enumElementToProto(EnumElement enumElem) {
        Boolean allowAlias = findOptionBoolean(ALLOW_ALIAS_OPTION, enumElem.getOptions());

        EnumDescriptorProto.Builder builder = EnumDescriptorProto.newBuilder()
                .setName(enumElem.getName());
        if (allowAlias != null) {
            DescriptorProtos.EnumOptions.Builder optionsBuilder = DescriptorProtos.EnumOptions.newBuilder()
                    .setAllowAlias(allowAlias);
            builder.mergeOptions(optionsBuilder.build());
        }
        for (EnumConstantElement constant : enumElem.getConstants()) {
            builder.addValue(EnumValueDescriptorProto.newBuilder()
                    .setName(constant.getName())
                    .setNumber(constant.getTag())
                    .build());
        }
        return builder.build();
    }

    private static String toMapEntry(String s) {
        if (s.contains("_")) {
            s = LOWER_UNDERSCORE.to(UPPER_CAMEL, s);
        }
        return s + MAP_ENTRY_SUFFIX;
    }

    private static Optional<OptionElement> findOption(String name, List<OptionElement> options) {
        return options.stream().filter(o -> o.getName().equals(name)).findFirst();
    }

    private static String findOptionString(String name, List<OptionElement> options) {
        return findOption(name, options).map(o -> o.getValue().toString()).orElse(null);
    }

    private static Boolean findOptionBoolean(String name, List<OptionElement> options) {
        return findOption(name, options).map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    }

    public static ProtoFileElement fileDescriptorToProtoFile(FileDescriptorProto file) {
        String packageName = file.getPackage();
        if ("".equals(packageName)) {
            packageName = null;
        }

        ProtoFile.Syntax syntax = null;
        switch (file.getSyntax()) {
            case PROTO2:
                syntax = ProtoFile.Syntax.PROTO_2;
                break;
            case PROTO3:
                syntax = ProtoFile.Syntax.PROTO_3;
                break;
            default:
                break;
        }
        ImmutableList.Builder<TypeElement> types = ImmutableList.builder();
        for (DescriptorProto md : file.getMessageTypeList()) {
            MessageElement message = toMessage(file, md);
            types.add(message);
        }
        for (EnumDescriptorProto ed : file.getEnumTypeList()) {
            EnumElement enumer = toEnum(ed);
            types.add(enumer);
        }
        ImmutableList.Builder<String> imports = ImmutableList.builder();
        ImmutableList.Builder<String> publicImports = ImmutableList.builder();
        List<String> dependencyList = file.getDependencyList();
        Set<Integer> publicDependencyList = new HashSet<>(file.getPublicDependencyList());
        for (int i = 0; i < dependencyList.size(); i++) {
            String depName = dependencyList.get(i);
            if (publicDependencyList.contains(i)) {
                publicImports.add(depName);
            } else {
                imports.add(depName);
            }
        }
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (file.getOptions().hasJavaPackage()) {
            OptionElement.Kind kind = OptionElement.Kind.STRING;
            OptionElement option = new OptionElement(JAVA_PACKAGE_OPTION, kind, file.getOptions().getJavaPackage(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaOuterClassname()) {
            OptionElement.Kind kind = OptionElement.Kind.STRING;
            OptionElement option = new OptionElement(JAVA_OUTER_CLASSNAME_OPTION, kind, file.getOptions().getJavaOuterClassname(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaMultipleFiles()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(JAVA_MULTIPLE_FILES_OPTION, kind, file.getOptions().getJavaMultipleFiles(), false);
            options.add(option);
        }
        return new ProtoFileElement(DEFAULT_LOCATION, packageName, syntax, imports.build(),
                publicImports.build(), types.build(), Collections.emptyList(), Collections.emptyList(),
                options.build());
    }

    private static MessageElement toMessage(FileDescriptorProto file, DescriptorProto descriptor) {
        String name = descriptor.getName();
        ImmutableList.Builder<FieldElement> fields = ImmutableList.builder();
        ImmutableList.Builder<TypeElement> nested = ImmutableList.builder();
        ImmutableList.Builder<ReservedElement> reserved = ImmutableList.builder();
        LinkedHashMap<String, ImmutableList.Builder<FieldElement>> oneofsMap = new LinkedHashMap<>();
        for (OneofDescriptorProto od : descriptor.getOneofDeclList()) {
            oneofsMap.put(od.getName(), ImmutableList.builder());
        }
        List<Map.Entry<String, ImmutableList.Builder<FieldElement>>> oneofs = new ArrayList<>(
                oneofsMap.entrySet());
        for (FieldDescriptorProto fd : descriptor.getFieldList()) {
            if (fd.hasOneofIndex()) {
                FieldElement field = toField(file, fd, true);
                oneofs.get(fd.getOneofIndex()).getValue().add(field);
            } else {
                FieldElement field = toField(file, fd, false);
                fields.add(field);
            }
        }
        for (DescriptorProto nestedDesc : descriptor.getNestedTypeList()) {
            MessageElement nestedMessage = toMessage(file, nestedDesc);
            nested.add(nestedMessage);
        }
        for (EnumDescriptorProto nestedDesc : descriptor.getEnumTypeList()) {
            EnumElement nestedEnum = toEnum(nestedDesc);
            nested.add(nestedEnum);
        }
        for (String reservedName : descriptor.getReservedNameList()) {
            ReservedElement reservedElem = new ReservedElement(DEFAULT_LOCATION, "",
                    Collections.singletonList(reservedName));
            reserved.add(reservedElem);
        }
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (descriptor.getOptions().hasMapEntry()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(MAP_ENTRY_OPTION, kind, descriptor.getOptions().getMapEntry(),
                    false);
            options.add(option);
        }
        return new MessageElement(DEFAULT_LOCATION, name, "", nested.build(), options.build(),
                reserved.build(), fields.build(),
                oneofs.stream().map(e -> toOneof(e.getKey(), e.getValue())).collect(Collectors.toList()),
                Collections.emptyList(), Collections.emptyList());
    }

    private static OneOfElement toOneof(String name, ImmutableList.Builder<FieldElement> fields) {
        return new OneOfElement(name, "", fields.build(), Collections.emptyList());
    }

    private static EnumElement toEnum(EnumDescriptorProto ed) {
        String name = ed.getName();
        ImmutableList.Builder<EnumConstantElement> constants = ImmutableList.builder();
        for (EnumValueDescriptorProto ev : ed.getValueList()) {
            ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
            constants.add(new EnumConstantElement(DEFAULT_LOCATION, ev.getName(), ev.getNumber(), "",
                    options.build()));
        }
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (ed.getOptions().hasAllowAlias()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(ALLOW_ALIAS_OPTION, kind, ed.getOptions().getAllowAlias(),
                    false);
            options.add(option);
        }
        return new EnumElement(DEFAULT_LOCATION, name, "", options.build(), constants.build());
    }

    private static FieldElement toField(FileDescriptorProto file, FieldDescriptorProto fd, boolean inOneof) {
        String name = fd.getName();
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (fd.getOptions().hasPacked()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(PACKED_OPTION, kind, fd.getOptions().getPacked(), false);
            options.add(option);
        }
        if (fd.hasJsonName()) {
            OptionElement.Kind kind = OptionElement.Kind.STRING;
            OptionElement option = new OptionElement(JSON_NAME_OPTION, kind, fd.getJsonName(), false);
            options.add(option);
        }
        String defaultValue = fd.hasDefaultValue() && fd.getDefaultValue() != null ? fd.getDefaultValue()
                : null;
        return new FieldElement(DEFAULT_LOCATION, inOneof ? null : label(file, fd), dataType(fd), name,
                defaultValue, fd.getNumber(), "", options.build());
    }

    private static Field.Label label(FileDescriptorProto file, FieldDescriptorProto fd) {
        boolean isProto3 = file.getSyntax().equals(PROTO3);
        switch (fd.getLabel()) {
            case LABEL_REQUIRED:
                return isProto3 ? null : Field.Label.REQUIRED;
            case LABEL_OPTIONAL:
                return isProto3 ? null : Field.Label.OPTIONAL;
            case LABEL_REPEATED:
                return Field.Label.REPEATED;
            default:
                throw new IllegalArgumentException("Unsupported label");
        }
    }

    private static String dataType(FieldDescriptorProto field) {
        if (field.hasTypeName()) {
            return field.getTypeName();
        } else {
            FieldDescriptorProto.Type type = field.getType();
            return FieldDescriptor.Type.valueOf(type).name().toLowerCase();
        }
    }

}
