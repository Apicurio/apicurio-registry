package io.apicurio.registry.utils.protobuf.schema;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.AnyProto;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DurationProto;
import com.google.protobuf.EmptyProto;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.WrappersProto;
import com.google.type.CalendarPeriodProto;
import com.google.type.ColorProto;
import com.google.type.DateProto;
import com.google.type.DayOfWeek;
import com.google.type.ExprProto;
import com.google.type.FractionProto;
import com.google.type.IntervalProto;
import com.google.type.LatLng;
import com.google.type.LocalizedTextProto;
import com.google.type.MoneyProto;
import com.google.type.MonthProto;
import com.google.type.PhoneNumberProto;
import com.google.type.PostalAddressProto;
import com.google.type.QuaternionProto;
import com.google.type.TimeOfDayProto;
import com.squareup.wire.Syntax;
import com.squareup.wire.schema.EnumConstant;
import com.squareup.wire.schema.EnumType;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.MessageType;
import com.squareup.wire.schema.OneOf;
import com.squareup.wire.schema.Options;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.Rpc;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.Service;
import com.squareup.wire.schema.Type;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.ExtensionsElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ReservedElement;
import com.squareup.wire.schema.internal.parser.RpcElement;
import com.squareup.wire.schema.internal.parser.ServiceElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import kotlin.ranges.IntRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.protobuf.DescriptorProtos.DescriptorProto;
import static com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import static com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import static com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import static com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import static com.google.protobuf.DescriptorProtos.FileOptions;
import static com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import static com.google.protobuf.DescriptorProtos.MethodOptions;
import static com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import static com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;

/**
 * @author Fabian Martinez, Ravindranath Kakarla
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
    private static final String DEPRECATED_OPTION = "deprecated";
    private static final String OPTIONAL = "optional";

    public static FileDescriptor[] baseDependencies() {
        //Support all the Protobuf WellKnownTypes
        //and the protos from Google API, https://github.com/googleapis/googleapis
        return new FileDescriptor[] {
            TimestampProto.getDescriptor().getFile(),
            WrappersProto.getDescriptor().getFile(),
            AnyProto.getDescriptor().getFile(),
            EmptyProto.getDescriptor().getFile(),
            DurationProto.getDescriptor().getFile(),
            TimeOfDayProto.getDescriptor().getFile(),
            DateProto.getDescriptor().getFile(),
            CalendarPeriodProto.getDescriptor().getFile(),
            ColorProto.getDescriptor().getFile(),
            DayOfWeek.getDescriptor().getFile(),
            LatLng.getDescriptor().getFile(),
            FractionProto.getDescriptor().getFile(),
            MoneyProto.getDescriptor().getFile(),
            MonthProto.getDescriptor().getFile(),
            PhoneNumberProto.getDescriptor().getFile(),
            PostalAddressProto.getDescriptor().getFile(),
            CalendarPeriodProto.getDescriptor().getFile(),
            LocalizedTextProto.getDescriptor().getFile(),
            IntervalProto.getDescriptor().getFile(),
            ExprProto.getDescriptor().getFile(),
            QuaternionProto.getDescriptor().getFile(),
            PostalAddressProto.getDescriptor().getFile()
        };
    }

    public static FileDescriptor protoFileToFileDescriptor(ProtoFileElement element)
        throws DescriptorValidationException {
        return protoFileToFileDescriptor(element, "default.proto");
    }

    public static FileDescriptor protoFileToFileDescriptor(ProtoFileElement element, String protoFileName) throws DescriptorValidationException {
        Objects.requireNonNull(element);
        Objects.requireNonNull(protoFileName);

        return protoFileToFileDescriptor(element.toSchema(), protoFileName,
            Optional.ofNullable(element.getPackageName()));
    }

    public static FileDescriptor protoFileToFileDescriptor(String schemaDefinition, String protoFileName, Optional<String> optionalPackageName)
        throws DescriptorValidationException {
        Objects.requireNonNull(schemaDefinition);
        Objects.requireNonNull(protoFileName);

        return FileDescriptor.buildFrom(toFileDescriptorProto(schemaDefinition, protoFileName, optionalPackageName), baseDependencies());
    }

    private static FileDescriptorProto toFileDescriptorProto(String schemaDefinition, String protoFileName, Optional<String> optionalPackageName) {
        final ProtobufSchemaLoader.ProtobufSchemaLoaderContext protobufSchemaLoaderContext;
        try {
            protobufSchemaLoaderContext =
                ProtobufSchemaLoader.loadSchema(optionalPackageName, protoFileName, schemaDefinition);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        FileDescriptorProto.Builder schema = FileDescriptorProto.newBuilder();

        ProtoFile element = protobufSchemaLoaderContext.getProtoFile();
        Schema schemaContext = protobufSchemaLoaderContext.getSchema();

        schema.setName(protoFileName);

        Syntax syntax = element.getSyntax();
        if (Syntax.PROTO_3.equals(syntax)) {
            schema.setSyntax(syntax.toString());
        }
        if (element.getPackageName() != null) {
            schema.setPackage(element.getPackageName());
        }

        for (ProtoType protoType : schemaContext.getTypes()) {
            if (!isParentLevelType(protoType, optionalPackageName)) {
                continue;
            }

            Type type = schemaContext.getType(protoType);
            if (type instanceof MessageType) {
                DescriptorProto
                    message = messageElementToDescriptorProto((MessageType) type, schemaContext, element);
                schema.addMessageType(message);
            } else if (type instanceof EnumType) {
                EnumDescriptorProto message = enumElementToProto((EnumType) type);
                schema.addEnumType(message);
            }
        }

        for (Service service : element.getServices()) {
            ServiceDescriptorProto serviceDescriptorProto = serviceElementToProto(service);
            schema.addService(serviceDescriptorProto);
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

    /**
     * When schema loader links the schema, it also includes google.protobuf types in it.
     * We want to ignore all the other types except for the ones that are present in the current file.
     * @return true if a type is a parent type, false otherwise.
     */
    private static boolean isParentLevelType(ProtoType protoType, Optional<String> optionalPackageName) {
        if (optionalPackageName.isPresent()) {
            String packageName = optionalPackageName.get();
            String typeName = protoType.toString();

            //If the type doesn't start with the package name, ignore it.
            if (!typeName.startsWith(packageName)) {
                return false;
            }
            //We only want to consider the parent level types. The list can contain following,
            //[io.apicurio.foo.bar.Customer.Address, io.apicurio.foo.bar.Customer, google.protobuf.Timestamp]
            //We want to only get the type "io.apicurio.foo.bar.Customer" which is parent level type.
            String[] typeNames = typeName.split(packageName)[1].split("\\.");
            boolean isNotNested = typeNames.length <= 2;
            return isNotNested;
        }

        //If package is not present,
        //TODO: Square wire has a bug loading schemas without packageName, update the logic when
        //https://github.com/square/wire/issues/2042 is fixed.
        return false;
    }

    private static DescriptorProto messageElementToDescriptorProto(
        MessageType messageElem, Schema schema, ProtoFile element) {
        ProtobufMessage message = new ProtobufMessage();
        message.protoBuilder().setName(messageElem.getType().getSimpleName());

        for (Type type : messageElem.getNestedTypes()) {
            if (type instanceof MessageType) {
                message.protoBuilder().addNestedType(
                    messageElementToDescriptorProto((MessageType) type, schema, element));
            } else if (type instanceof EnumType) {
                message.protoBuilder().addEnumType(enumElementToProto((EnumType) type));
            }
        }

        buildFields(messageElem, message, schema, element);

        for (ReservedElement reserved : messageElem.toElement().getReserveds()) {
            for (Object elem : reserved.getValues()) {
                if (elem instanceof String) {
                    message.protoBuilder().addReservedName((String) elem);
                } else if (elem instanceof Integer) {
                    int tag = (Integer) elem;
                    DescriptorProto.ReservedRange.Builder rangeBuilder = DescriptorProto.ReservedRange.newBuilder()
                            .setStart(tag)
                            .setEnd(tag + 1);
                    message.protoBuilder().addReservedRange(rangeBuilder.build());
                } else if (elem instanceof IntRange) {
                    IntRange range = (IntRange) elem;
                    DescriptorProto.ReservedRange.Builder rangeBuilder = DescriptorProto.ReservedRange.newBuilder()
                            .setStart(range.getStart())
                            .setEnd(range.getEndInclusive() + 1);
                    message.protoBuilder().addReservedRange(rangeBuilder.build());
                } else {
                    throw new IllegalStateException(
                            "Unsupported reserved type: " + elem.getClass().getName());
                }
            }
        }
        for (ExtensionsElement extensions : messageElem.toElement().getExtensions()) {
            for (Object elem : extensions.getValues()) {
                if (elem instanceof Integer) {
                    int tag = (Integer) elem;
                    DescriptorProto.ExtensionRange.Builder extensionBuilder = DescriptorProto.ExtensionRange.newBuilder()
                            .setStart(tag)
                            .setEnd(tag + 1);
                    message.protoBuilder().addExtensionRange(extensionBuilder.build());
                } else if (elem instanceof IntRange) {
                    IntRange range = (IntRange) elem;
                    DescriptorProto.ExtensionRange.Builder extensionBuilder = DescriptorProto.ExtensionRange.newBuilder()
                            .setStart(range.getStart())
                            .setEnd(range.getEndInclusive() + 1);
                    message.protoBuilder().addExtensionRange(extensionBuilder.build());
                } else {
                    throw new IllegalStateException(
                            "Unsupported extension type: " + elem.getClass().getName());
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

    private static void buildFields(MessageType messageElem, ProtobufMessage message, Schema schema, ProtoFile element) {
        final Predicate<Field> isProto3Optional =
            field ->
                Field.Label.OPTIONAL.equals(field.getLabel()) && Syntax.PROTO_3.equals(element.getSyntax());

        final List<OneOf> oneOfs = messageElem.getOneOfs();
        final List<OneOf> proto3OptionalOneOfs =
            messageElem.getFieldsAndOneOfFields()
                .stream()
                .filter(isProto3Optional)
                .map(FileDescriptorUtils::getProto3OptionalField)
                .collect(Collectors.toList());

        //Proto3 Optionals are considered as "synthetic-oneofs" by Protobuf compiler.
        oneOfs.addAll(proto3OptionalOneOfs);

        final Function<String, Optional<OneOf>> findOneOfByFieldName = fieldName -> {
            for (OneOf oneOf : oneOfs) {
                if (oneOf.getFields().stream().map(Field::getName).anyMatch(f -> f.equals(fieldName))) {
                    return Optional.of(oneOf);
                }
            }
            return Optional.empty();
        };

        //Add all the declared fields first skipping oneOfs.
        for (final Field field : messageElem.getDeclaredFields()) {
            final Optional<OneOf> optionalOneOf = findOneOfByFieldName.apply(field.getName());
            if (!optionalOneOf.isPresent()) {
                addField(field, message, schema);
                continue;
            }
        }

        final Set<OneOf> addedOneOfs = new LinkedHashSet<>();

        //Add the oneOfs next including Proto3 Optionals.
        for (final OneOf oneOfField : oneOfs) {
            if (addedOneOfs.contains(oneOfField)) {
                continue;
            }

            Boolean isProto3OptionalField = null;
            if (proto3OptionalOneOfs.contains(oneOfField)) {
                isProto3OptionalField = true;
            }
            addOneOfField(oneOfField, message, schema, isProto3OptionalField);
            addedOneOfs.add(oneOfField);
        }
    }

    private static void addField(Field field, ProtobufMessage message, Schema schema) {
        Field.Label fieldLabel = field.getLabel();
        //Fields are optional by default in Proto3.
        String label = fieldLabel != null ? fieldLabel.toString().toLowerCase() : OPTIONAL;

        ProtoType protoType = field.getType();
        String fieldTypeName = String.valueOf(protoType);
        ProtoType keyType = protoType.getKeyType();
        ProtoType valueType = protoType.getValueType();
        // Map fields are only permitted in messages
        if (protoType.isMap() && keyType != null && valueType != null) {
            label = "repeated";
            fieldTypeName = toMapEntry(field.getName());
            ProtobufMessage protobufMapMessage = new ProtobufMessage();
            DescriptorProto.Builder mapMessage = protobufMapMessage
                .protoBuilder()
                .setName(fieldTypeName)
                .mergeOptions(DescriptorProtos.MessageOptions.newBuilder()
                    .setMapEntry(true)
                    .build());

            protobufMapMessage
                .addField(null, null, keyType.getSimpleName(), KEY_FIELD, 1, null, null, null, null, null, null);
            protobufMapMessage
                .addField(null, null, valueType.getSimpleName(), VALUE_FIELD, 2, null, null, null, null, null, null);
            message.protoBuilder().addNestedType(mapMessage.build());
        }

        String jsonName = field.getDeclaredJsonName();
        Boolean isDeprecated = findOptionBoolean(DEPRECATED_OPTION, field.getOptions());
        Boolean isPacked = findOptionBoolean(PACKED_OPTION, field.getOptions());

        String fieldType = determineFieldType(field, schema);

        message.addField(label, fieldType, fieldTypeName, field.getName(), field.getTag(), field.getDefault(),
            jsonName, isDeprecated, isPacked, null, null);
    }

    private static String determineFieldType(Field field, Schema schema) {
        ProtoType protoType = field.getType();
        Type typeReference = schema.getType(protoType);
        if (typeReference != null) {
            if (typeReference instanceof MessageType) {
                return "message";
            }
            if (typeReference instanceof EnumType) {
                return "enum";
            }
        }
        return null;
    }

    /**
     * Proto3 optional fields are "synthetic one-ofs" and are written as one-of fields over the wire.
     * This method generates the synthetic one-of from a Proto3 optional field.
     */
    private static OneOf getProto3OptionalField(Field field) {
        return new OneOf("_" + field.getName(), "", Collections.singletonList(field));
    }

    private static void addOneOfField(OneOf oneOf, ProtobufMessage message, Schema schema, Boolean isProto3Optional) {
        OneofDescriptorProto.Builder oneofBuilder = OneofDescriptorProto.newBuilder().setName(oneOf.getName());
        message.protoBuilder().addOneofDecl(oneofBuilder);

        for (Field oneOfField : oneOf.getFields()) {
            String oneOfJsonName = findOptionString(JSON_NAME_OPTION, oneOfField.getOptions());
            Boolean oneOfIsDeprecated = findOptionBoolean(DEPRECATED_OPTION, oneOfField.getOptions());

            message.addFieldDescriptorProto(
                FieldDescriptorProto.Label.LABEL_OPTIONAL,
                determineFieldType(oneOfField, schema),
                String.valueOf(oneOfField.getType()),
                oneOfField.getName(),
                oneOfField.getTag(),
                oneOfField.getDefault(),
                oneOfJsonName,
                oneOfIsDeprecated,
                null,
                message.protoBuilder().getOneofDeclCount() - 1,
                isProto3Optional);
        }
    }

    private static EnumDescriptorProto enumElementToProto(EnumType enumElem) {
        Boolean allowAlias = findOptionBoolean(ALLOW_ALIAS_OPTION, enumElem.getOptions());

        EnumDescriptorProto.Builder builder = EnumDescriptorProto.newBuilder()
                .setName(enumElem.getName());
        if (allowAlias != null) {
            DescriptorProtos.EnumOptions.Builder optionsBuilder = DescriptorProtos.EnumOptions.newBuilder()
                    .setAllowAlias(allowAlias);
            builder.mergeOptions(optionsBuilder.build());
        }
        for (EnumConstant constant : enumElem.getConstants()) {
            builder.addValue(EnumValueDescriptorProto.newBuilder()
                    .setName(constant.getName())
                    .setNumber(constant.getTag())
                    .build());
        }
        return builder.build();
    }

    private static DescriptorProtos.ServiceDescriptorProto serviceElementToProto(Service serviceElem) {
        ServiceDescriptorProto.Builder builder = ServiceDescriptorProto.newBuilder().setName(serviceElem.name());

        for (Rpc rpc : serviceElem.rpcs()) {
            MethodDescriptorProto.Builder methodBuilder = MethodDescriptorProto
                    .newBuilder()
                    .setName(rpc.getName())
                    .setInputType(getTypeName(rpc.getRequestType().toString()))
                    .setOutputType(getTypeName(rpc.getResponseType().toString()));
            if (rpc.getRequestStreaming()) {
                methodBuilder.setClientStreaming(rpc.getRequestStreaming());
            }
            if (rpc.getResponseStreaming()) {
                methodBuilder.setServerStreaming(rpc.getResponseStreaming());
            }
            Boolean deprecated = findOptionBoolean(DEPRECATED_OPTION, rpc.getOptions());
            if (deprecated != null) {
                MethodOptions.Builder optionsBuilder = MethodOptions.newBuilder()
                        .setDeprecated(deprecated);
                methodBuilder.mergeOptions(optionsBuilder.build());
            }

            builder.addMethod(methodBuilder.build());
        }

        Boolean deprecated = findOptionBoolean(DEPRECATED_OPTION, serviceElem.options());
        if (deprecated != null) {
            DescriptorProtos.ServiceOptions.Builder optionsBuilder = DescriptorProtos.ServiceOptions.newBuilder()
                    .setDeprecated(deprecated);
            builder.mergeOptions(optionsBuilder.build());
        }

        return builder.build();
    }

    private static String toMapEntry(String s) {
        if (s.contains("_")) {
            s = LOWER_UNDERSCORE.to(UPPER_CAMEL, s);
        }
        return s + MAP_ENTRY_SUFFIX;
    }

    private static Optional<OptionElement> findOption(String name, Options options) {
        return options.getElements().stream().filter(o -> o.getName().equals(name)).findFirst();
    }

    private static String findOptionString(String name, Options options) {
        return findOption(name, options).map(o -> o.getValue().toString()).orElse(null);
    }

    private static Boolean findOptionBoolean(String name, Options options) {
        return findOption(name, options).map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    }

    public static ProtoFileElement fileDescriptorToProtoFile(FileDescriptorProto file) {
        String packageName = file.getPackage();
        if ("".equals(packageName)) {
            packageName = null;
        }

        Syntax syntax = null;
        switch (file.getSyntax()) {
            case PROTO2:
                syntax = Syntax.PROTO_2;
                break;
            case PROTO3:
                syntax = Syntax.PROTO_3;
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
        ImmutableList.Builder<ServiceElement> services = ImmutableList.builder();
        for (ServiceDescriptorProto sv : file.getServiceList()) {
            ServiceElement service = toService(sv);
            services.add(service);
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
                publicImports.build(), types.build(), services.build(), Collections.emptyList(),
                options.build());
    }

    private static MessageElement toMessage(FileDescriptorProto file, DescriptorProto descriptor) {
        String name = descriptor.getName();
        ImmutableList.Builder<FieldElement> fields = ImmutableList.builder();
        ImmutableList.Builder<TypeElement> nested = ImmutableList.builder();
        ImmutableList.Builder<ReservedElement> reserved = ImmutableList.builder();
        ImmutableList.Builder<ExtensionsElement> extensions = ImmutableList.builder();
        LinkedHashMap<String, ImmutableList.Builder<FieldElement>> oneofsMap = new LinkedHashMap<>();
        for (OneofDescriptorProto od : descriptor.getOneofDeclList()) {
            oneofsMap.put(od.getName(), ImmutableList.builder());
        }
        List<Map.Entry<String, ImmutableList.Builder<FieldElement>>> oneofs = new ArrayList<>(
                oneofsMap.entrySet());
        List<FieldElement> proto3OptionalFields = new ArrayList<>();
        for (FieldDescriptorProto fd : descriptor.getFieldList()) {
            if (fd.hasProto3Optional()) {
                proto3OptionalFields.add(toField(file, fd, false));
                continue;
            }
            if (fd.hasOneofIndex()) {
                FieldElement field = toField(file, fd, true);
                oneofs.get(fd.getOneofIndex()).getValue().add(field);
            } else {
                FieldElement field = toField(file, fd, false);
                fields.add(field);
            }
        }
        fields.addAll(proto3OptionalFields);
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
        for (DescriptorProto.ReservedRange reservedRange : descriptor.getReservedRangeList()) {
            List<IntRange> values = new ArrayList<>();
            int start = reservedRange.getStart();
            int end = reservedRange.getEnd() - 1;
            values.add(new IntRange(start, end));
            ReservedElement reservedElem = new ReservedElement(DEFAULT_LOCATION, "", values);
            reserved.add(reservedElem);
        }
        for (DescriptorProto.ExtensionRange extensionRange : descriptor.getExtensionRangeList()) {
            List<IntRange> values = new ArrayList<>();
            int start = extensionRange.getStart();
            int end = extensionRange.getEnd() - 1;
            values.add(new IntRange(start, end));
            ExtensionsElement extensionsElement = new ExtensionsElement(DEFAULT_LOCATION, "", values);
            extensions.add(extensionsElement);
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
                oneofs.stream()
                    //Ignore oneOfs with no fields (like Proto3 Optional)
                    .filter(e -> e.getValue().build().size() != 0)
                    .map(e -> toOneof(e.getKey(), e.getValue())).collect(Collectors.toList()),
                extensions.build(), Collections.emptyList());
    }

    private static OneOfElement toOneof(String name, ImmutableList.Builder<FieldElement> fields) {
        return new OneOfElement(name, "", fields.build(), Collections.emptyList(), Collections.emptyList());
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

    private static ServiceElement toService(DescriptorProtos.ServiceDescriptorProto sv) {
        String name = sv.getName();
        ImmutableList.Builder<RpcElement> rpcs = ImmutableList.builder();
        for (MethodDescriptorProto md : sv.getMethodList()) {
            rpcs.add(new RpcElement(DEFAULT_LOCATION, md.getName(), "", md.getInputType(),
                    md.getOutputType(), md.getClientStreaming(), md.getServerStreaming(),
                    getOptionList(md.getOptions().hasDeprecated(), md.getOptions().getDeprecated())));
        }

        return new ServiceElement(DEFAULT_LOCATION, name, "", rpcs.build(),
                getOptionList(sv.getOptions().hasDeprecated(), sv.getOptions().getDeprecated()));
    }

    private static FieldElement toField(FileDescriptorProto file, FieldDescriptorProto fd, boolean inOneof) {
        String name = fd.getName();
        DescriptorProtos.FieldOptions fieldDescriptorOptions = fd.getOptions();
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (fieldDescriptorOptions.hasPacked()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(PACKED_OPTION, kind, fd.getOptions().getPacked(), false);
            options.add(option);
        }
        if (fd.hasJsonName()) {
            OptionElement.Kind kind = OptionElement.Kind.STRING;
            OptionElement option = new OptionElement(JSON_NAME_OPTION, kind, fd.getJsonName(), false);
            options.add(option);
        }
        if (fieldDescriptorOptions.hasDeprecated()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(DEPRECATED_OPTION, kind, fieldDescriptorOptions.getDeprecated(),
                false);
            options.add(option);
        }

        //Implicitly jsonName to null as Options is already setting it. Setting it here results in duplicate json_name
        //option in inferred schema.
        String jsonName = null;
        String defaultValue = fd.hasDefaultValue() && fd.getDefaultValue() != null ? fd.getDefaultValue()
                : null;
        return new FieldElement(DEFAULT_LOCATION, inOneof ? null : label(file, fd), dataType(fd), name,
                defaultValue, jsonName, fd.getNumber(), "", options.build());
    }

    private static Field.Label label(FileDescriptorProto file, FieldDescriptorProto fd) {
        boolean isProto3 = file.getSyntax().equals(PROTO3);
        switch (fd.getLabel()) {
            case LABEL_REQUIRED:
                return isProto3 ? null : Field.Label.REQUIRED;
            case LABEL_OPTIONAL:
                //If it's a Proto3 optional, we have to print the optional label.
                return isProto3 && !fd.hasProto3Optional() ? null : Field.Label.OPTIONAL;
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

    private static List<OptionElement> getOptionList(boolean hasDeprecated, boolean deprecated) {
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (hasDeprecated) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(DEPRECATED_OPTION, kind, deprecated, false);
            options.add(option);
        }

        return options.build();
    }

    private static String getTypeName(String typeName) {
        return typeName.startsWith(".") ? typeName : "." + typeName;
    }

}
