/*
 * Copyright 2026 Red Hat
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

package io.apicurio.schema.validation.avro;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.schema.validation.ErrorMessageExtractor;
import io.apicurio.schema.validation.ValidationError;
import io.apicurio.schema.validation.ValidationResult;
import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides validation APIs for data objects against an Avro Schema.
 * Schemas are managed in Apicurio Registry and downloaded and cached at runtime by this library.
 *
 * @author Carles Arnal
 */
public class AvroValidator {

    private SchemaResolver<Schema, GenericRecord> schemaResolver;
    private ArtifactReference artifactReference;

    /**
     * Creates the Avro validator.
     * If artifactReference is provided it must exist in Apicurio Registry.
     *
     * @param configuration     , configuration properties for {@link DefaultSchemaResolver} for config properties see {@link SchemaResolverConfig}
     * @param artifactReference , optional {@link ArtifactReference} used as a static configuration to always use the same schema for validation when invoking {@link AvroValidator#validateByArtifactReference(GenericRecord)}
     */
    public AvroValidator(Map<String, Object> configuration, Optional<ArtifactReference> artifactReference) {
        this.schemaResolver = new DefaultSchemaResolver<>();
        this.schemaResolver.configure(configuration, new AvroSchemaParser());
        artifactReference.ifPresent(reference -> this.artifactReference = reference);
    }

    protected AvroValidator() {
        //for tests
    }

    /**
     * Validates the provided GenericRecord against an Avro Schema.
     * The Avro Schema will be fetched from Apicurio Registry using the {@link ArtifactReference} provided in the constructor, this artifact must exist in the registry.
     *
     * @param record , the GenericRecord that will be validated against the Avro Schema.
     * @return ValidationResult
     */
    public ValidationResult validateByArtifactReference(GenericRecord record) {
        Objects.requireNonNull(this.artifactReference,
                "ArtifactReference must be provided when creating AvroValidator in order to use this feature");
        try {
            SchemaLookupResult<Schema> schema = this.schemaResolver.resolveSchemaByArtifactReference(this.artifactReference);
            return validate(schema.getParsedSchema().getParsedSchema(), record);
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + ErrorMessageExtractor.extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    /**
     * Validates the provided JSON string against an Avro Schema.
     * The Avro Schema will be fetched from Apicurio Registry using the {@link ArtifactReference} provided in the constructor, this artifact must exist in the registry.
     *
     * @param json , the JSON string that will be validated against the Avro Schema.
     * @return ValidationResult
     */
    public ValidationResult validateByArtifactReference(String json) {
        Objects.requireNonNull(this.artifactReference,
                "ArtifactReference must be provided when creating AvroValidator in order to use this feature");
        try {
            SchemaLookupResult<Schema> schema = this.schemaResolver.resolveSchemaByArtifactReference(this.artifactReference);
            return validateJson(schema.getParsedSchema().getParsedSchema(), json);
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + ErrorMessageExtractor.extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    /**
     * Validates the payload of the provided Record against an Avro Schema.
     * This method will resolve the schema based on the configuration provided in the constructor. See {@link SchemaResolverConfig} for configuration options and features of {@link SchemaResolver}.
     * You can use {@link io.apicurio.schema.validation.SchemaValidationRecord} as the implementation for the provided record or you can use an implementation of your own.
     * Opposite to {@link AvroValidator#validateByArtifactReference(GenericRecord)} this method allows to dynamically use a different schema for validating each record.
     *
     * @param record , the record used to resolve the schema used for validation and to provide the payload to validate.
     * @return ValidationResult
     */
    public ValidationResult validate(Record<GenericRecord> record) {
        try {
            SchemaLookupResult<Schema> schema = this.schemaResolver.resolveSchema(record);
            return validate(schema.getParsedSchema().getParsedSchema(), record.payload());
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + ErrorMessageExtractor.extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    protected ValidationResult validate(Schema schema, GenericRecord record) {
        List<ValidationError> errors = new ArrayList<>();
        validateRecord(schema, record, "", errors);
        if (!errors.isEmpty()) {
            return ValidationResult.fromErrors(errors);
        }
        return ValidationResult.successful();
    }

    protected ValidationResult validateJson(Schema schema, String json) {
        try {
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(schema, json);
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            reader.read(null, decoder);
            return ValidationResult.successful();
        } catch (AvroTypeException e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError(e.getMessage(), "TYPE_ERROR")
            ));
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError(e.getMessage(), "VALIDATION_ERROR")
            ));
        }
    }

    private void validateRecord(Schema schema, GenericRecord record, String path, List<ValidationError> errors) {
        if (schema.getType() != Schema.Type.RECORD) {
            errors.add(new ValidationError("Expected RECORD schema but got " + schema.getType(), path));
            return;
        }

        Schema recordSchema = record.getSchema();
        for (Schema.Field field : schema.getFields()) {
            String fieldPath = path.isEmpty() ? field.name() : path + "." + field.name();

            // Check if the field exists in the record's own schema
            if (recordSchema.getField(field.name()) == null) {
                if (!isNullable(field.schema()) && field.defaultVal() == null) {
                    errors.add(new ValidationError("Missing required field: " + field.name(), fieldPath));
                }
                continue;
            }

            Object value = record.get(field.name());

            if (value == null) {
                if (!isNullable(field.schema()) && field.defaultVal() == null) {
                    errors.add(new ValidationError("Missing required field: " + field.name(), fieldPath));
                }
            } else {
                validateValue(field.schema(), value, fieldPath, errors);
            }
        }
    }

    private void validateValue(Schema schema, Object value, String path, List<ValidationError> errors) {
        switch (schema.getType()) {
            case UNION:
                boolean matched = false;
                for (Schema unionType : schema.getTypes()) {
                    List<ValidationError> unionErrors = new ArrayList<>();
                    validateValue(unionType, value, path, unionErrors);
                    if (unionErrors.isEmpty()) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    errors.add(new ValidationError(
                        "Value does not match any type in union for field: " + path, path));
                }
                break;
            case RECORD:
                if (value instanceof GenericRecord) {
                    validateRecord(schema, (GenericRecord) value, path, errors);
                } else {
                    errors.add(new ValidationError(
                        "Expected GenericRecord but got " + value.getClass().getSimpleName(), path));
                }
                break;
            case ENUM:
                if (!schema.getEnumSymbols().contains(value.toString())) {
                    errors.add(new ValidationError(
                        "Invalid enum value '" + value + "'. Expected one of: " + schema.getEnumSymbols(), path));
                }
                break;
            case ARRAY:
                if (value instanceof java.util.Collection) {
                    int i = 0;
                    for (Object item : (java.util.Collection<?>) value) {
                        validateValue(schema.getElementType(), item, path + "[" + i + "]", errors);
                        i++;
                    }
                }
                break;
            case MAP:
                if (value instanceof java.util.Map) {
                    for (Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) value).entrySet()) {
                        validateValue(schema.getValueType(), entry.getValue(),
                            path + "[\"" + entry.getKey() + "\"]", errors);
                    }
                }
                break;
            case NULL:
                if (value != null) {
                    errors.add(new ValidationError("Expected null but got " + value.getClass().getSimpleName(), path));
                }
                break;
            default:
                // Primitive types (STRING, INT, LONG, FLOAT, DOUBLE, BOOLEAN, BYTES, FIXED)
                // are validated by the Avro framework itself when setting values on GenericRecord
                break;
        }
    }

    private boolean isNullable(Schema schema) {
        if (schema.getType() == Schema.Type.NULL) {
            return true;
        }
        if (schema.getType() == Schema.Type.UNION) {
            return schema.getTypes().stream().anyMatch(s -> s.getType() == Schema.Type.NULL);
        }
        return false;
    }

}
