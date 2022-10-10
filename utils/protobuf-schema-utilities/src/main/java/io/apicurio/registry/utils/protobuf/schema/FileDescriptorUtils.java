package io.apicurio.registry.utils.protobuf.schema;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.*;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
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
import metadata.ProtobufSchemaMetadata;
import additionalTypes.Decimals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
 * @author Fabian Martinez, Ravindranath Kakarla, Carles Arnal
 */
public class FileDescriptorUtils {

    public static final Location DEFAULT_LOCATION = Location.get("");

    private static final String PROTO2 = "proto2";
    private static final String PROTO3 = "proto3";
    private static final String ALLOW_ALIAS_OPTION = "allow_alias";
    private static final String MAP_ENTRY_OPTION = "map_entry";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static final String MAP_ENTRY_SUFFIX = "Entry";
    private static final String DEPRECATED_OPTION = "deprecated";
    private static final String OPTIONAL = "optional";

    // field options
    private static final String PACKED_OPTION = "packed";
    private static final String JSON_NAME_OPTION = "json_name";
    private static final String CTYPE_OPTION = "ctype";
    private static final String JSTYPE_OPTION = "jstype";
    // file options
    private static final String CC_GENERIC_SERVICES_OPTION = "cc_generic_services";
    private static final String CC_ENABLE_ARENAS_OPTION = "cc_enable_arenas";
    private static final String CSHARP_NAMESPACE_OPTION = "csharp_namespace";
    private static final String GO_PACKAGE_OPTION = "go_package";
    private static final String JAVA_GENERIC_SERVICES_OPTION = "java_generic_services";
    private static final String JAVA_MULTIPLE_FILES_OPTION = "java_multiple_files";
    private static final String JAVA_OUTER_CLASSNAME_OPTION = "java_outer_classname";
    private static final String JAVA_PACKAGE_OPTION = "java_package";
    private static final String JAVA_STRING_CHECK_UTF8_OPTION = "java_string_check_utf8";
    private static final String OBJC_CLASS_PREFIX_OPTION = "objc_class_prefix";
    private static final String OPTIMIZE_FOR_OPTION = "optimize_for";
    private static final String PHP_CLASS_PREFIX_OPTION = "php_class_prefix";
    private static final String PHP_GENERIC_SERVICES_OPTION = "php_generic_services";
    private static final String PHP_METADATA_NAMESPACE_OPTION = "php_metadata_namespace";
    private static final String PHP_NAMESPACE_OPTION = "php_namespace";
    private static final String PY_GENERIC_SERVICES_OPTION = "py_generic_services";
    private static final String RUBY_PACKAGE_OPTION = "ruby_package";
    private static final String SWIFT_PREFIX_OPTION = "swift_prefix";
    // message options
    private static final String NO_STANDARD_DESCRIPTOR_OPTION = "no_standard_descriptor_accessor";
    // rpc options
    private static final String IDEMPOTENCY_LEVEL_OPTION = "idempotency_level";

    private static final OptionElement.Kind booleanKind =  OptionElement.Kind.BOOLEAN;
    private static final OptionElement.Kind stringKind =  OptionElement.Kind.STRING;
    private static final OptionElement.Kind enumKind =  OptionElement.Kind.ENUM;

    public static FileDescriptor[] baseDependencies() {
        //Support all the Protobuf WellKnownTypes
        //and the protos from Google API, https://github.com/googleapis/googleapis
        return new FileDescriptor[] {
            ApiProto.getDescriptor().getFile(),
            FieldMaskProto.getDescriptor().getFile(),
            SourceContextProto.getDescriptor().getFile(),
            StructProto.getDescriptor().getFile(),
            TypeProto.getDescriptor().getFile(),
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
            PostalAddressProto.getDescriptor().getFile(),
            ProtobufSchemaMetadata.getDescriptor().getFile(),
            Decimals.getDescriptor().getFile()
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
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setJavaPackage(javaPackageName).build();
            schema.mergeOptions(options);
        }

        String javaOuterClassname = findOptionString(JAVA_OUTER_CLASSNAME_OPTION, element.getOptions());
        if (javaOuterClassname != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setJavaOuterClassname(javaOuterClassname).build();
            schema.mergeOptions(options);
        }

        Boolean javaMultipleFiles = findOptionBoolean(JAVA_MULTIPLE_FILES_OPTION, element.getOptions());
        if (javaMultipleFiles != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setJavaMultipleFiles(javaMultipleFiles).build();
            schema.mergeOptions(options);
        }

        Boolean javaStringCheckUtf8 = findOptionBoolean(JAVA_STRING_CHECK_UTF8_OPTION, element.getOptions());
        if (javaStringCheckUtf8 != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setJavaStringCheckUtf8(javaStringCheckUtf8).build();
            schema.mergeOptions(options);
        }

        Boolean javaGenericServices = findOptionBoolean(JAVA_GENERIC_SERVICES_OPTION, element.getOptions());
        if (javaGenericServices != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setJavaGenericServices(javaGenericServices).build();
            schema.mergeOptions(options);
        }

        Boolean ccGenericServices = findOptionBoolean(CC_GENERIC_SERVICES_OPTION, element.getOptions());
        if (ccGenericServices != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setCcGenericServices(ccGenericServices).build();
            schema.mergeOptions(options);
        }

        Boolean ccEnableArenas = findOptionBoolean(CC_ENABLE_ARENAS_OPTION, element.getOptions());
        if (ccEnableArenas != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setCcEnableArenas(ccEnableArenas).build();
            schema.mergeOptions(options);
        }

        String csharpNamespace = findOptionString(CSHARP_NAMESPACE_OPTION, element.getOptions());
        if (csharpNamespace != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setCsharpNamespace(csharpNamespace).build();
            schema.mergeOptions(options);
        }

        String goPackageName = findOptionString(GO_PACKAGE_OPTION, element.getOptions());
        if (goPackageName != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setGoPackage(goPackageName).build();
            schema.mergeOptions(options);
        }

        String objcClassPrefix = findOptionString(OBJC_CLASS_PREFIX_OPTION, element.getOptions());
        if (objcClassPrefix != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setObjcClassPrefix(objcClassPrefix).build();
            schema.mergeOptions(options);
        }

        Boolean phpGenericServices = findOptionBoolean(PHP_GENERIC_SERVICES_OPTION, element.getOptions());
        if (phpGenericServices != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setPhpGenericServices(phpGenericServices).build();
            schema.mergeOptions(options);
        }

        String phpClassPrefix = findOptionString(PHP_CLASS_PREFIX_OPTION, element.getOptions());
        if (phpClassPrefix != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setPhpClassPrefix(phpClassPrefix).build();
            schema.mergeOptions(options);
        }

        String phpMetadataNamespace = findOptionString(PHP_METADATA_NAMESPACE_OPTION, element.getOptions());
        if (phpMetadataNamespace != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setPhpMetadataNamespace(phpMetadataNamespace).build();
            schema.mergeOptions(options);
        }

        String phpNamespace = findOptionString(PHP_NAMESPACE_OPTION, element.getOptions());
        if (phpNamespace != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setPhpNamespace(phpNamespace).build();
            schema.mergeOptions(options);
        }

        Boolean pyGenericServices = findOptionBoolean(PY_GENERIC_SERVICES_OPTION, element.getOptions());
        if (pyGenericServices != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setPyGenericServices(pyGenericServices).build();
            schema.mergeOptions(options);
        }

        String rubyPackage = findOptionString(RUBY_PACKAGE_OPTION, element.getOptions());
        if (rubyPackage != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setRubyPackage(rubyPackage).build();
            schema.mergeOptions(options);
        }

        String swiftPrefix = findOptionString(SWIFT_PREFIX_OPTION, element.getOptions());
        if (swiftPrefix != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setSwiftPrefix(swiftPrefix).build();
            schema.mergeOptions(options);
        }

        FileOptions.OptimizeMode optimizeFor = findOption(OPTIMIZE_FOR_OPTION, element.getOptions())
                .map(o -> FileOptions.OptimizeMode.valueOf(o.getValue().toString())).orElse(null);
        if (optimizeFor != null) {
            FileOptions options = DescriptorProtos.FileOptions.newBuilder().setOptimizeFor(optimizeFor).build();
            schema.mergeOptions(options);
        }

        return schema.build();
    }

    /**
     * When schema loader links the schema, it also includes google.protobuf types in it.
     * We want to ignore all the other types except for the ones that are present in the current file.
     *
     * @return true if a type is a parent type, false otherwise.
     */
    private static boolean isParentLevelType(ProtoType protoType, Optional<String> optionalPackageName) {
        String typeName = protoType.toString();
        if (optionalPackageName.isPresent()) {
            String packageName = optionalPackageName.get();

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

        //In case the package is not defined, we select the types that are not google types or metadata types.
        return !typeName.startsWith("google.type") && !typeName.startsWith("google.protobuf")
                && !typeName.startsWith("metadata")
                && !typeName.startsWith("additionalTypes");
    }

    private static DescriptorProto messageElementToDescriptorProto(
            MessageType messageElem, Schema schema, ProtoFile element) {
        ProtobufMessage message = new ProtobufMessage();
        message.protoBuilder().setName(messageElem.getType().getSimpleName());

        Comparator<Location> locationComparator =
                Comparator.comparing(Location::getLine).thenComparing(Location::getColumn);
        Map<Location, DescriptorProto> allNestedTypes = new TreeMap<>(locationComparator);
        List<FieldDescriptorProto> allFields = new ArrayList<>();

        for (Type type : messageElem.getNestedTypes()) {
            if (type instanceof MessageType) {
                allNestedTypes.put(type.getLocation(),
                        messageElementToDescriptorProto((MessageType) type, schema, element));
            } else if (type instanceof EnumType) {
                message.protoBuilder().addEnumType(enumElementToProto((EnumType) type));
            }
        }

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
                Field.Label fieldLabel = field.getLabel();
                //Fields are optional by default in Proto3.
                String label = fieldLabel != null ? fieldLabel.toString().toLowerCase() : OPTIONAL;

                String fieldType = determineFieldType(field.getType(), schema);
                ProtoType protoType = field.getType();
                String fieldTypeName = String.valueOf(protoType);
                ProtoType keyType = protoType.getKeyType();
                ProtoType valueType = protoType.getValueType();
                // Map fields are only permitted in messages
                if (protoType.isMap() && keyType != null && valueType != null) {
                    label = "repeated";
                    fieldType = "message";
                    String fieldMapEntryName = toMapEntry(field.getName());
                    // Map entry field name is capitalized
                    fieldMapEntryName = fieldMapEntryName.substring(0, 1).toUpperCase() + fieldMapEntryName.substring(1);
                    // Map field type name is resolved with reference to the package
                    fieldTypeName = String.format("%s.%s", messageElem.getType(), fieldMapEntryName);
                    ProtobufMessage protobufMapMessage = new ProtobufMessage();
                    DescriptorProto.Builder mapMessage = protobufMapMessage
                            .protoBuilder()
                            .setName(fieldMapEntryName)
                            .mergeOptions(DescriptorProtos.MessageOptions.newBuilder()
                                    .setMapEntry(true)
                                    .build());

                    protobufMapMessage
                            .addField(OPTIONAL, determineFieldType(keyType, schema), String.valueOf(keyType), KEY_FIELD, 1, null, null, null, null, null, null, null, null, null, null);
                    protobufMapMessage
                            .addField(OPTIONAL, determineFieldType(valueType, schema), String.valueOf(valueType), VALUE_FIELD, 2, null, null, null, null, null, null, null, null, null, null);
                    allNestedTypes.put(field.getLocation(), mapMessage.build());
                }

                String jsonName = getDefaultJsonName(field.getName()).equals(field.getDeclaredJsonName())
                        ? null : field.getDeclaredJsonName();
                Boolean isDeprecated = findOptionBoolean(DEPRECATED_OPTION, field.getOptions());
                Boolean isPacked = findOptionBoolean(PACKED_OPTION, field.getOptions());
                DescriptorProtos.FieldOptions.CType cType = findOption(CTYPE_OPTION, field.getOptions())
                        .map(o -> DescriptorProtos.FieldOptions.CType.valueOf(o.getValue().toString())).orElse(null);
                DescriptorProtos.FieldOptions.JSType jsType = findOption(JSTYPE_OPTION, field.getOptions())
                        .map(o -> DescriptorProtos.FieldOptions.JSType.valueOf(o.getValue().toString())).orElse(null);
                String metadataKey = findOptionString(ProtobufSchemaMetadata.metadataKey.getDescriptor().getFullName(),
                        field.getOptions());
                String metadataValue = findOptionString(ProtobufSchemaMetadata.metadataValue.getDescriptor().getFullName(),
                        field.getOptions());

                allFields.add(ProtobufMessage.buildFieldDescriptorProto(
                        label, fieldType, fieldTypeName, field.getName(), field.getTag(), field.getDefault(),
                        jsonName, isDeprecated, isPacked, cType, jsType, metadataKey, metadataValue, null, null));
            }
        }

        final Set<OneOf> addedOneOfs = new LinkedHashSet<>();

        //Add the oneOfs next including Proto3 Optionals.
        for (final OneOf oneOf : oneOfs) {
            if (addedOneOfs.contains(oneOf)) {
                continue;
            }

            Boolean isProto3OptionalField = null;
            if (proto3OptionalOneOfs.contains(oneOf)) {
                isProto3OptionalField = true;
            }
            OneofDescriptorProto.Builder oneofBuilder = OneofDescriptorProto.newBuilder().setName(oneOf.getName());
            message.protoBuilder().addOneofDecl(oneofBuilder);

            for (Field oneOfField : oneOf.getFields()) {
                String oneOfJsonName = getDefaultJsonName(oneOfField.getName()).equals(oneOfField.getDeclaredJsonName())
                        ? null : oneOfField.getDeclaredJsonName();
                Boolean oneOfIsDeprecated = findOptionBoolean(DEPRECATED_OPTION, oneOfField.getOptions());
                Boolean oneOfIsPacked = findOptionBoolean(PACKED_OPTION, oneOfField.getOptions());
                DescriptorProtos.FieldOptions.CType oneOfCType = findOption(CTYPE_OPTION, oneOfField.getOptions())
                        .map(o -> DescriptorProtos.FieldOptions.CType.valueOf(o.getValue().toString())).orElse(null);
                DescriptorProtos.FieldOptions.JSType oneOfJsType = findOption(JSTYPE_OPTION, oneOfField.getOptions())
                        .map(o -> DescriptorProtos.FieldOptions.JSType.valueOf(o.getValue().toString())).orElse(null);
                String metadataKey = findOptionString(ProtobufSchemaMetadata.metadataKey.getDescriptor().getFullName(),
                        oneOfField.getOptions());
                String metadataValue = findOptionString(ProtobufSchemaMetadata.metadataValue.getDescriptor().getFullName(),
                        oneOfField.getOptions());

                allFields.add(ProtobufMessage.buildFieldDescriptorProto(
                        OPTIONAL,
                        determineFieldType(oneOfField.getType(), schema),
                        String.valueOf(oneOfField.getType()),
                        oneOfField.getName(),
                        oneOfField.getTag(),
                        oneOfField.getDefault(),
                        oneOfJsonName,
                        oneOfIsDeprecated,
                        oneOfIsPacked,
                        oneOfCType,
                        oneOfJsType,
                        metadataKey,
                        metadataValue,
                        message.protoBuilder().getOneofDeclCount() - 1,
                        isProto3OptionalField));

            }
            addedOneOfs.add(oneOf);
        }

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
        Boolean noStandardDescriptorAccessor = findOptionBoolean(NO_STANDARD_DESCRIPTOR_OPTION, messageElem.getOptions());
        if (noStandardDescriptorAccessor != null) {
            DescriptorProtos.MessageOptions.Builder optionsBuilder = DescriptorProtos.MessageOptions.newBuilder()
                    .setNoStandardDescriptorAccessor(noStandardDescriptorAccessor);
            message.protoBuilder().mergeOptions(optionsBuilder.build());
        }

        message.protoBuilder().addAllNestedType(allNestedTypes.values());
        message.protoBuilder().addAllField(allFields);
        return message.build();
    }

    private static String determineFieldType(ProtoType protoType, Schema schema) {
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
            MethodOptions.IdempotencyLevel idempotencyLevel = findOption(IDEMPOTENCY_LEVEL_OPTION, rpc.getOptions())
                    .map(o -> MethodOptions.IdempotencyLevel.valueOf(o.getValue().toString()))
                    .orElse(null);
            if (idempotencyLevel != null) {
                MethodOptions.Builder optionsBuilder = MethodOptions.newBuilder()
                        .setIdempotencyLevel(idempotencyLevel);
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

    public static ProtoFileElement fileDescriptorWithDepsToProtoFile(
            FileDescriptor file, Map<String, ProtoFileElement> dependencies
    ) {
        for (FileDescriptor dependency : file.getDependencies()) {
            String depName = dependency.getName();
            dependencies.put(depName, fileDescriptorWithDepsToProtoFile(dependency, dependencies));
        }
        return fileDescriptorToProtoFile(file.toProto());
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
            OptionElement option = new OptionElement(JAVA_PACKAGE_OPTION, stringKind, file.getOptions().getJavaPackage(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaOuterClassname()) {
            OptionElement option = new OptionElement(JAVA_OUTER_CLASSNAME_OPTION, stringKind, file.getOptions().getJavaOuterClassname(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaMultipleFiles()) {
            OptionElement option = new OptionElement(JAVA_MULTIPLE_FILES_OPTION, booleanKind, file.getOptions().getJavaMultipleFiles(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaGenericServices()) {
            OptionElement option = new OptionElement(JAVA_GENERIC_SERVICES_OPTION, booleanKind, file.getOptions().getJavaGenericServices(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaStringCheckUtf8()) {
            OptionElement option = new OptionElement(JAVA_STRING_CHECK_UTF8_OPTION, booleanKind, file.getOptions().getJavaStringCheckUtf8(), false);
            options.add(option);
        }
        if (file.getOptions().hasCcGenericServices()) {
            OptionElement option = new OptionElement(CC_GENERIC_SERVICES_OPTION, booleanKind, file.getOptions().getCcGenericServices(), false);
            options.add(option);
        }
        if (file.getOptions().hasCcEnableArenas()) {
            OptionElement option = new OptionElement(CC_ENABLE_ARENAS_OPTION, booleanKind, file.getOptions().getCcEnableArenas(), false);
            options.add(option);
        }
        if (file.getOptions().hasCsharpNamespace()) {
            OptionElement option = new OptionElement(CSHARP_NAMESPACE_OPTION, stringKind, file.getOptions().getCsharpNamespace(), false);
            options.add(option);
        }
        if (file.getOptions().hasGoPackage()) {
            OptionElement option = new OptionElement(GO_PACKAGE_OPTION, stringKind, file.getOptions().getGoPackage(), false);
            options.add(option);
        }
        if (file.getOptions().hasObjcClassPrefix()) {
            OptionElement option = new OptionElement(OBJC_CLASS_PREFIX_OPTION, stringKind, file.getOptions().getObjcClassPrefix(), false);
            options.add(option);
        }
        if (file.getOptions().hasPhpClassPrefix()) {
            OptionElement option = new OptionElement(PHP_CLASS_PREFIX_OPTION, stringKind, file.getOptions().getPhpClassPrefix(), false);
            options.add(option);
        }
        if (file.getOptions().hasPhpGenericServices()) {
            OptionElement option = new OptionElement(PHP_GENERIC_SERVICES_OPTION, booleanKind, file.getOptions().getPhpGenericServices(), false);
            options.add(option);
        }
        if (file.getOptions().hasPhpMetadataNamespace()) {
            OptionElement option = new OptionElement(PHP_METADATA_NAMESPACE_OPTION, stringKind, file.getOptions().getPhpMetadataNamespace(), false);
            options.add(option);
        }
        if (file.getOptions().hasPhpNamespace()) {
            OptionElement option = new OptionElement(PHP_NAMESPACE_OPTION, stringKind, file.getOptions().getPhpNamespace(), false);
            options.add(option);
        }
        if (file.getOptions().hasPyGenericServices()) {
            OptionElement option = new OptionElement(PY_GENERIC_SERVICES_OPTION, booleanKind, file.getOptions().getPyGenericServices(), false);
            options.add(option);
        }
        if (file.getOptions().hasRubyPackage()) {
            OptionElement option = new OptionElement(RUBY_PACKAGE_OPTION, stringKind, file.getOptions().getRubyPackage(), false);
            options.add(option);
        }
        if (file.getOptions().hasSwiftPrefix()) {
            OptionElement option = new OptionElement(SWIFT_PREFIX_OPTION, stringKind, file.getOptions().getSwiftPrefix(), false);
            options.add(option);
        }
        if (file.getOptions().hasOptimizeFor()) {
            OptionElement option = new OptionElement(OPTIMIZE_FOR_OPTION, enumKind, file.getOptions().getOptimizeFor(), false);
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
            OptionElement option = new OptionElement(MAP_ENTRY_OPTION, booleanKind, descriptor.getOptions().getMapEntry(),
                    false);
            options.add(option);
        }
        if (descriptor.getOptions().hasNoStandardDescriptorAccessor()) {
            OptionElement option = new OptionElement(NO_STANDARD_DESCRIPTOR_OPTION, booleanKind,
                    descriptor.getOptions().getNoStandardDescriptorAccessor(), false);
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
            OptionElement option = new OptionElement(ALLOW_ALIAS_OPTION, booleanKind, ed.getOptions().getAllowAlias(),
                    false);
            options.add(option);
        }

        ImmutableList.Builder<ReservedElement> reserved = ImmutableList.builder();
        Iterator reservedRangeIterator = ed.getReservedRangeList().iterator();

        ReservedElement reservedElem;
        while (reservedRangeIterator.hasNext()) {
            EnumDescriptorProto.EnumReservedRange range = (EnumDescriptorProto.EnumReservedRange) reservedRangeIterator.next();
            reservedElem = toReserved(range);
            reserved.add(reservedElem);
        }

        reservedRangeIterator = ed.getReservedNameList().iterator();

        while (reservedRangeIterator.hasNext()) {
            String reservedName = (String) reservedRangeIterator.next();
            reservedElem = new ReservedElement(DEFAULT_LOCATION, "", Collections.singletonList(reservedName));
            reserved.add(reservedElem);
        }

        return new EnumElement(DEFAULT_LOCATION, name, "", options.build(), constants.build(), reserved.build());
    }

    private static ServiceElement toService(DescriptorProtos.ServiceDescriptorProto sv) {
        String name = sv.getName();
        ImmutableList.Builder<RpcElement> rpcs = ImmutableList.builder();
        for (MethodDescriptorProto md : sv.getMethodList()) {
            rpcs.add(new RpcElement(DEFAULT_LOCATION, md.getName(), "", md.getInputType(),
                    md.getOutputType(), md.getClientStreaming(), md.getServerStreaming(), getMethodOptionList(md.getOptions())));
        }

        return new ServiceElement(DEFAULT_LOCATION, name, "", rpcs.build(),
                getOptionList(sv.getOptions().hasDeprecated(), sv.getOptions().getDeprecated()));
    }

    private static FieldElement toField(FileDescriptorProto file, FieldDescriptorProto fd, boolean inOneof) {
        String name = fd.getName();
        DescriptorProtos.FieldOptions fieldDescriptorOptions = fd.getOptions();
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (fieldDescriptorOptions.hasPacked()) {
            OptionElement option = new OptionElement(PACKED_OPTION, booleanKind, fd.getOptions().getPacked(), false);
            options.add(option);
        }
        if (fd.hasJsonName() && !fd.getJsonName().equals(getDefaultJsonName(name))) {
            OptionElement option = new OptionElement(JSON_NAME_OPTION, stringKind, fd.getJsonName(), false);
            options.add(option);
        }
        if (fieldDescriptorOptions.hasDeprecated()) {
            OptionElement option = new OptionElement(DEPRECATED_OPTION, booleanKind, fieldDescriptorOptions.getDeprecated(),
                    false);
            options.add(option);
        }
        if (fieldDescriptorOptions.hasCtype()) {
            OptionElement option = new OptionElement(CTYPE_OPTION, enumKind, fieldDescriptorOptions.getCtype(), false);
            options.add(option);
        }
        if (fieldDescriptorOptions.hasJstype()) {
            OptionElement option = new OptionElement(JSTYPE_OPTION, enumKind, fieldDescriptorOptions.getJstype(), false);
            options.add(option);
        }
        if (fieldDescriptorOptions.hasExtension(ProtobufSchemaMetadata.metadataKey)) {
            OptionElement keyOption = new OptionElement(
                    ProtobufSchemaMetadata.metadataKey.getDescriptor().getFullName(), stringKind,
                    fieldDescriptorOptions.getExtension(ProtobufSchemaMetadata.metadataKey), false);
            options.add(keyOption);
        }
        if (fieldDescriptorOptions.hasExtension(ProtobufSchemaMetadata.metadataValue)) {
            OptionElement valueOption = new OptionElement(
                    ProtobufSchemaMetadata.metadataValue.getDescriptor().getFullName(), stringKind,
                    fieldDescriptorOptions.getExtension(ProtobufSchemaMetadata.metadataValue), false);
            options.add(valueOption);
        }

        //Implicitly jsonName to null as Options is already setting it. Setting it here results in duplicate json_name
        //option in inferred schema.
        String jsonName = null;
        String defaultValue = fd.hasDefaultValue() && fd.getDefaultValue() != null ? fd.getDefaultValue()
                : null;
        return new FieldElement(DEFAULT_LOCATION, inOneof ? null : label(file, fd), dataType(fd), name,
                defaultValue, jsonName, fd.getNumber(), "", options.build());
    }

    private static ReservedElement toReserved(EnumDescriptorProto.EnumReservedRange range) {
        List<Object> values = new ArrayList<>();
        int start = range.getStart();
        int end = range.getEnd();
        values.add(start == end - 1 ? start : new IntRange(start, end - 1));
        return new ReservedElement(DEFAULT_LOCATION, "", values);
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
            OptionElement option = new OptionElement(DEPRECATED_OPTION, booleanKind, deprecated, false);
            options.add(option);
        }

        return options.build();
    }

    private static List<OptionElement> getMethodOptionList(MethodOptions methodOptions) {
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (methodOptions.hasDeprecated()) {
            OptionElement option = new OptionElement(DEPRECATED_OPTION, booleanKind, methodOptions.getDeprecated(), false);
            options.add(option);
        }
        if (methodOptions.hasIdempotencyLevel()) {
            OptionElement option = new OptionElement(IDEMPOTENCY_LEVEL_OPTION, enumKind, methodOptions.getIdempotencyLevel(), false);
            options.add(option);
        }

        return options.build();
    }

    private static String getTypeName(String typeName) {
        return typeName.startsWith(".") ? typeName : "." + typeName;
    }

    // Default json_name is constructed following lower camel case
    // https://github.com/protocolbuffers/protobuf/blob/3e1967e10be786062ccd026275866c3aef487eba/src/google/protobuf/descriptor.cc#L405
    private static String getDefaultJsonName(String fieldName) {
        String[] parts = fieldName.split("_");
        String defaultJsonName = parts[0];
        for (int i = 1; i < parts.length; ++i) {
            defaultJsonName += parts[i].substring(0, 1).toUpperCase() + parts[i].substring(1);
        }
        return defaultJsonName;
    }


    public static Descriptors.Descriptor toDescriptor(String name, ProtoFileElement protoFileElement, Map<String, ProtoFileElement> dependencies) {
        return toDynamicSchema(name, protoFileElement, dependencies).getMessageDescriptor(name);
    }

    public static MessageElement firstMessage(ProtoFileElement fileElement) {
        for (TypeElement typeElement : fileElement.getTypes()) {
            if (typeElement instanceof MessageElement) {
                return (MessageElement) typeElement;
            }
        }
        //Intended null return
        return null;
    }

    /*
     * DynamicSchema is used as a temporary helper class and should not be exposed in the API.
     */
    private static DynamicSchema toDynamicSchema(
            String name, ProtoFileElement rootElem, Map<String, ProtoFileElement> dependencies
    ) {

        DynamicSchema.Builder schema = DynamicSchema.newBuilder();
        try {
            Syntax syntax = rootElem.getSyntax();
            if (syntax != null) {
                schema.setSyntax(syntax.toString());
            }
            if (rootElem.getPackageName() != null) {
                schema.setPackage(rootElem.getPackageName());
            }
            for (TypeElement typeElem : rootElem.getTypes()) {
                if (typeElem instanceof MessageElement) {
                    MessageDefinition message = toDynamicMessage((MessageElement) typeElem);
                    schema.addMessageDefinition(message);
                } else if (typeElem instanceof EnumElement) {
                    EnumDefinition enumer = toDynamicEnum((EnumElement) typeElem);
                    schema.addEnumDefinition(enumer);
                }
            }
            for (String ref : rootElem.getImports()) {
                ProtoFileElement dep = dependencies.get(ref);
                if (dep != null) {
                    schema.addDependency(ref);
                    schema.addSchema(toDynamicSchema(ref, dep, dependencies));
                }
            }
            for (String ref : rootElem.getPublicImports()) {
                ProtoFileElement dep = dependencies.get(ref);
                if (dep != null) {
                    schema.addPublicDependency(ref);
                    schema.addSchema(toDynamicSchema(ref, dep, dependencies));
                }
            }
            String javaPackageName = findOption("java_package", rootElem.getOptions())
                    .map(o -> o.getValue().toString()).orElse(null);
            if (javaPackageName != null) {
                schema.setJavaPackage(javaPackageName);
            }
            String javaOuterClassname = findOption("java_outer_classname", rootElem.getOptions())
                    .map(o -> o.getValue().toString()).orElse(null);
            if (javaOuterClassname != null) {
                schema.setJavaOuterClassname(javaOuterClassname);
            }
            Boolean javaMultipleFiles = findOption("java_multiple_files", rootElem.getOptions())
                    .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
            if (javaMultipleFiles != null) {
                schema.setJavaMultipleFiles(javaMultipleFiles);
            }
            schema.setName(name);
            return schema.build();
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MessageDefinition toDynamicMessage(
            MessageElement messageElem
    ) {
        MessageDefinition.Builder message = MessageDefinition.newBuilder(messageElem.getName());
        for (TypeElement type : messageElem.getNestedTypes()) {
            if (type instanceof MessageElement) {
                message.addMessageDefinition(toDynamicMessage((MessageElement) type));
            } else if (type instanceof EnumElement) {
                message.addEnumDefinition(toDynamicEnum((EnumElement) type));
            }
        }
        Set<String> added = new HashSet<>();
        for (OneOfElement oneof : messageElem.getOneOfs()) {
            MessageDefinition.OneofBuilder oneofBuilder = message.addOneof(oneof.getName());
            for (FieldElement field : oneof.getFields()) {
                String defaultVal = field.getDefaultValue();
                String jsonName = findOption("json_name", field.getOptions())
                        .map(o -> o.getValue().toString()).orElse(null);
                oneofBuilder.addField(
                        field.getType(),
                        field.getName(),
                        field.getTag(),
                        defaultVal,
                        jsonName
                );
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
            String defaultVal = field.getDefaultValue();
            String jsonName = field.getJsonName();
            Boolean isPacked = findOption("packed", field.getOptions())
                    .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
            ProtoType protoType = ProtoType.get(fieldType);
            ProtoType keyType = protoType.getKeyType();
            ProtoType valueType = protoType.getValueType();
            // Map fields are only permitted in messages
            if (protoType.isMap() && keyType != null && valueType != null) {
                label = "repeated";
                fieldType = toMapEntry(field.getName());
                MessageDefinition.Builder mapMessage = MessageDefinition.newBuilder(fieldType);
                mapMessage.setMapEntry(true);
                mapMessage.addField(null, keyType.getSimpleName(), KEY_FIELD, 1, null);
                mapMessage.addField(null, valueType.getSimpleName(), VALUE_FIELD, 2, null);
                message.addMessageDefinition(mapMessage.build());
            }
            message.addField(
                    label,
                    fieldType,
                    field.getName(),
                    field.getTag(),
                    defaultVal,
                    jsonName,
                    isPacked
            );
        }
        for (ReservedElement reserved : messageElem.getReserveds()) {
            for (Object elem : reserved.getValues()) {
                if (elem instanceof String) {
                    message.addReservedName((String) elem);
                } else if (elem instanceof Integer) {
                    int tag = (Integer) elem;
                    message.addReservedRange(tag, tag);
                } else if (elem instanceof IntRange) {
                    IntRange range = (IntRange) elem;
                    message.addReservedRange(range.getStart(), range.getEndInclusive());
                } else {
                    throw new IllegalStateException("Unsupported reserved type: " + elem.getClass()
                            .getName());
                }
            }
        }
        Boolean isMapEntry = findOption("map_entry", messageElem.getOptions())
                .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
        if (isMapEntry != null) {
            message.setMapEntry(isMapEntry);
        }
        return message.build();
    }

    public static Optional<OptionElement> findOption(String name, List<OptionElement> options) {
        return options.stream().filter(o -> o.getName().equals(name)).findFirst();
    }

    private static EnumDefinition toDynamicEnum(EnumElement enumElem) {
        Boolean allowAlias = findOption("allow_alias", enumElem.getOptions())
                .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
        EnumDefinition.Builder enumer = EnumDefinition.newBuilder(enumElem.getName(), allowAlias);
        for (EnumConstantElement constant : enumElem.getConstants()) {
            enumer.addValue(constant.getName(), constant.getTag());
        }
        return enumer.build();
    }

    public static String toMapField(String s) {
        if (s.endsWith(MAP_ENTRY_SUFFIX)) {
            s = s.substring(0, s.length() - MAP_ENTRY_SUFFIX.length());
            s = UPPER_CAMEL.to(LOWER_UNDERSCORE, s);
        }
        return s;
    }
}
