package io.apicurio.schema.validation.protobuf;

import com.google.protobuf.Message;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.protobuf.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import io.apicurio.schema.validation.ErrorMessageExtractor;
import io.apicurio.schema.validation.SchemaValidationRecord;
import io.apicurio.schema.validation.ValidationError;
import io.apicurio.schema.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides validation APIs for Protobuf objects against a Protobuf Schema.
 * Schemas are managed in Apicurio Registry and downloaded and cached at runtime by this library.
 *
 * @author Carles Arnal
 */
public class ProtobufValidator {

    private final ProtobufSchemaParser<Message> protobufSchemaUSchemaParser;
    private SchemaResolver<ProtobufSchema, Message> schemaResolver;
    private ArtifactReference artifactReference;

    /**
     * Creates the Protobuf validator.
     * If artifactReference is provided it must exist in Apicurio Registry.
     *
     * @param configuration,     configuration properties for {@link DefaultSchemaResolver} for config properties see {@link SchemaResolverConfig}
     * @param artifactReference, optional {@link ArtifactReference} used as a static configuration to always use the same schema for validation when invoking validateArtifactByReference.
     */
    public ProtobufValidator(Map<String, Object> configuration,
            Optional<ArtifactReference> artifactReference) {
        this.schemaResolver = new DefaultSchemaResolver();
        this.protobufSchemaUSchemaParser = new ProtobufSchemaParser<>();
        this.schemaResolver.configure(configuration, protobufSchemaUSchemaParser);
        artifactReference.ifPresent(reference -> this.artifactReference = reference);
    }

    protected ProtobufValidator() {
        //for tests
        this.protobufSchemaUSchemaParser = new ProtobufSchemaParser<>();
    }

    /**
     * Validates the provided object against a Protobuf Schema.
     * The Protobuf Schema will be fetched from Apicurio Registry using the {@link ArtifactReference} provided in the constructor, this artifact must exist in the registry.
     *
     * @param bean , the object that will be validated against the Protobuf Schema, must implement {@link Message}.
     * @return ValidationResult
     */
    public ValidationResult validateByArtifactReference(Message bean) {
        Objects.requireNonNull(this.artifactReference,
                "ArtifactReference must be provided when creating JsonValidator in order to use this feature");
        try {
            SchemaLookupResult<ProtobufSchema> schema = this.schemaResolver.resolveSchemaByArtifactReference(
                    this.artifactReference);
            return validate(schema.getParsedSchema(), new SchemaValidationRecord<>(bean, null));
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + ErrorMessageExtractor.extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    /**
     * Validates the payload of the provided Record against a Protobuf Schema.
     * This method will resolve the schema based on the configuration provided in the constructor. See {@link SchemaResolverConfig} for configuration options and features of {@link SchemaResolver}.
     * You can use {@link SchemaValidationRecord} as the implementation for the provided record or you can use an implementation of your own.
     * Opposite to validateArtifactByReference this method allow to dynamically use a different schema for validating each record.
     *
     * @param record , the record used to resolve the schema used for validation and to provide the payload to validate.
     * @return ValidationResult
     */
    public ValidationResult validate(Record<Message> record) {
        try {
            SchemaLookupResult<ProtobufSchema> schema = this.schemaResolver.resolveSchema(record);
            return validate(schema.getParsedSchema(), record);
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + ErrorMessageExtractor.extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    protected ValidationResult validate(ParsedSchema<ProtobufSchema> schema, Record<Message> record) {
        if (schema.getParsedSchema() != null && schema.getParsedSchema().getFileDescriptor()
                .findMessageTypeByName(record.payload().getDescriptorForType().getName()) == null) {

            return ValidationResult.fromErrors(List.of(new ValidationError(
                    "Missing message type " + record.payload().getDescriptorForType().getName()
                            + " in the protobuf schema", "")));
        }

        List<ProtobufDifference> diffs = validate(schema, record.payload());
        if (!diffs.isEmpty()) {
            List<ValidationError> validationErrors = new ArrayList<>();
            diffs.forEach(diff -> validationErrors.add(new ValidationError(diff.getMessage(), "")));
            return ValidationResult.fromErrors(validationErrors);
        }

        return ValidationResult.successful();
    }

    private List<ProtobufDifference> validate(ParsedSchema<ProtobufSchema> schemaFromRegistry, Message data) {
        ProtobufFile fileBefore = schemaFromRegistry.getParsedSchema().getProtobufFile();
        ProtoFileElement afterElement = normalizeFieldTypes(
                protobufSchemaUSchemaParser.toProtoFileElement(data.getDescriptorForType().getFile()));
        ProtobufFile fileAfter = new ProtobufFile(afterElement);
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        return checker.findDifferences();
    }

    /**
     * Normalizes fully qualified type names in a ProtoFileElement to use short form.
     * Compiled protobuf descriptors store types as fully qualified (e.g., .package.Type),
     * while parsed proto text uses short names (e.g., Type). This normalization ensures
     * consistent type name format for comparison.
     */
    private ProtoFileElement normalizeFieldTypes(ProtoFileElement element) {
        String packageName = element.getPackageName();
        List<TypeElement> normalizedTypes = element.getTypes().stream()
                .map(type -> type instanceof MessageElement
                        ? normalizeMessage((MessageElement) type, packageName)
                        : type)
                .collect(Collectors.toList());

        return new ProtoFileElement(element.getLocation(), element.getPackageName(),
                element.getSyntax(), element.getImports(), element.getPublicImports(),
                element.getWeakImports(), normalizedTypes, element.getServices(),
                element.getExtendDeclarations(), element.getOptions());
    }

    private MessageElement normalizeMessage(MessageElement msg, String packageName) {
        List<FieldElement> normalizedFields = msg.getFields().stream()
                .map(field -> normalizeFieldElement(field, packageName))
                .collect(Collectors.toList());

        List<OneOfElement> normalizedOneOfs = new ArrayList<>();
        for (OneOfElement oneOf : msg.getOneOfs()) {
            List<FieldElement> oneOfFields = oneOf.getFields().stream()
                    .map(field -> normalizeFieldElement(field, packageName))
                    .collect(Collectors.toList());
            normalizedOneOfs.add(new OneOfElement(oneOf.getName(), oneOf.getDocumentation(),
                    oneOfFields, oneOf.getGroups(), oneOf.getOptions(), oneOf.getLocation()));
        }

        List<TypeElement> normalizedNestedTypes = msg.getNestedTypes().stream()
                .map(type -> type instanceof MessageElement
                        ? normalizeMessage((MessageElement) type, packageName)
                        : type)
                .collect(Collectors.toList());

        return new MessageElement(msg.getLocation(), msg.getName(), msg.getDocumentation(),
                normalizedNestedTypes, msg.getOptions(), msg.getReserveds(), normalizedFields,
                normalizedOneOfs, msg.getExtensions(), msg.getGroups(), msg.getExtendDeclarations());
    }

    private FieldElement normalizeFieldElement(FieldElement field, String packageName) {
        String type = normalizeTypeName(field.getType(), packageName);
        if (type.equals(field.getType())) {
            return field;
        }
        return new FieldElement(field.getLocation(), field.getLabel(), type,
                field.getName(), field.getDefaultValue(), field.getJsonName(),
                field.getTag(), field.getDocumentation(), field.getOptions());
    }

    private String normalizeTypeName(String type, String packageName) {
        if (type == null || !type.startsWith(".")) {
            return type;
        }
        // Strip leading dot and package prefix for same-package types
        // e.g., ".io.apicurio.schema.validation.protobuf.ref.Address" -> "Address"
        if (packageName != null) {
            String prefix = "." + packageName + ".";
            if (type.startsWith(prefix)) {
                return type.substring(prefix.length());
            }
        }
        // For other packages, strip just the leading dot
        // e.g., ".other.package.Type" -> "other.package.Type"
        return type.substring(1);
    }

}