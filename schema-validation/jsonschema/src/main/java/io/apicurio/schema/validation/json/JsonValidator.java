/*
 * Copyright 2022 Red Hat
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

package io.apicurio.schema.validation.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.Error;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.schema.validation.ErrorMessageExtractor;
import io.apicurio.schema.validation.ValidationError;
import io.apicurio.schema.validation.ValidationResult;
import org.json.JSONObject;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides validation APIs for JSON objects (Java objects, byte[], StringJSONObject,...) against a JSON Schema.
 * Schemas are managed in Apicurio Registry and downloaded and cached at runtime by this library.
 *
 * @author Fabian Martinez
 */
public class JsonValidator {

    private SchemaResolver<Schema, Object> schemaResolver;
    private ArtifactReference artifactReference;

    static final ObjectMapper mapper = new JsonMapper();

    /**
     * Creates the JSON validator.
     * If artifactReference is provided it must exist in Apicurio Registry.
     *
     * @param configuration     , configuration properties for {@link DefaultSchemaResolver} for config properties see {@link SchemaResolverConfig}
     * @param artifactReference , optional {@link ArtifactReference} used as a static configuration to always use the same schema for validation when invoking {@link JsonValidator#validateByArtifactReference(Object)}
     */
    public JsonValidator(Map<String, Object> configuration, Optional<ArtifactReference> artifactReference) {
        this.schemaResolver = new DefaultSchemaResolver<>();
        this.schemaResolver.configure(configuration, new JsonSchemaParser());
        artifactReference.ifPresent(reference -> this.artifactReference = reference);
    }

    protected JsonValidator() {
        //for tests
    }

    /**
     * Validates the provided object against a JSON Schema.
     * The JSON Schema will be fetched from Apicurio Registry using the {@link ArtifactReference} provided in the constructor, this artifact must exist in the registry.
     *
     * @param bean, the object that will be validate against the JSON Schema, can be a custom Java bean, String, byte[], InputStream, {@link JSONObject} or Map.
     * @return JsonValidationResult
     */
    public ValidationResult validateByArtifactReference(Object bean) {
        Objects.requireNonNull(this.artifactReference, "ArtifactReference must be provided when creating JsonValidator in order to use this feature");
        try {
            SchemaLookupResult<Schema> schema = this.schemaResolver.resolveSchemaByArtifactReference(this.artifactReference);
            JsonNode jsonPayload = createJSONObject(bean);
            return validate(schema.getParsedSchema().getParsedSchema(), jsonPayload);
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + ErrorMessageExtractor.extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    /**
     * Validates the payload of the provided Record against a JSON Schema.
     * This method will resolve the schema based on the configuration provided in the constructor. See {@link SchemaResolverConfig} for configuration options and features of {@link SchemaResolver}.
     * You can use {@link io.apicurio.schema.validation.SchemaValidationRecord} as the implementation for the provided record or you can use an implementation of your own.
     * Opposite to {@link JsonValidator#validateByArtifactReference(Object)} this method allow to dynamically use a different schema for validating each record.
     *
     * @param record , the record used to resolve the schema used for validation and to provide the payload to validate.
     * @return JsonValidationResult
     */
    public ValidationResult validate(Record<Object> record) {
        try {
            SchemaLookupResult<Schema> schema = this.schemaResolver.resolveSchema(record);
            JsonNode jsonPayload = createJSONObject(record.payload());
            return validate(schema.getParsedSchema().getParsedSchema(), jsonPayload);
        } catch (Exception e) {
            return ValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + ErrorMessageExtractor.extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    protected ValidationResult validate(Schema schema, JsonNode jsonPayload) {
        List<Error> validate = schema.validate(jsonPayload);

        if (!validate.isEmpty()) {
            return ValidationResult.fromErrors(extractValidationErrors(validate));
        }

        return ValidationResult.successful();
    }

    private JsonNode createJSONObject(Object bean) {
        if (bean instanceof ByteBuffer) {
            try (InputStream inputStream = new ByteBufferInputStream((ByteBuffer) bean);
                    JsonParser parser = mapper.createParser(inputStream)) {
                return parser.readValueAs(JsonNode.class);
            }
            catch (JacksonException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            return mapper.convertValue(bean, JsonNode.class);
        }
    }

    private List<ValidationError> extractValidationErrors(List<Error> validationErrors) {
        List<ValidationError> errors = new ArrayList<>();
        if (validationErrors != null && !validationErrors.isEmpty()) {
            for (Error cause : validationErrors) {
                ValidationError error = new ValidationError(cause.getMessage(), cause.getKeyword());
                errors.add(error);
            }
        }
        return errors;
    }

    public static class JsonSchemaParser implements SchemaParser<Schema, Object> {
        @Override
        public String artifactType() {
            return ArtifactType.JSON;
        }

        @Override
        public Schema parseSchema(byte[] rawSchema, Map<String, ParsedSchema<Schema>> resolvedReferences) {
            Map<String, String> referenceSchemas = new HashMap<>();

            resolveReferences(resolvedReferences, referenceSchemas);

            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
                    builder -> builder.schemas(referenceSchemas));

            return registry.getSchema(IoUtil.toString(rawSchema));
        }

        private void resolveReferences(Map<String, ParsedSchema<Schema>> resolvedReferences, Map<String, String> referenceSchemas) {
            resolvedReferences.forEach((referenceName, schema) -> {
                if (schema.hasReferences()) {
                    resolveReferences(schema.getSchemaReferences()
                            .stream()
                            .collect(Collectors.toMap(parsedSchema -> parsedSchema.getParsedSchema().getId(), parsedSchema -> parsedSchema)), referenceSchemas);

                }
                referenceSchemas.put(schema.getParsedSchema().getId(), IoUtil.toString(schema.getRawSchema()));
            });
        }

        @Override
        public ParsedSchema<Schema> getSchemaFromData(Record<Object> data) {
            //not supported yet?
            return null;
        }

        @Override
        public ParsedSchema<Schema> getSchemaFromData(Record<Object> record, boolean dereference) {
            //not supported yet?
            return null;
        }

        @Override
        public boolean supportsExtractSchemaFromData() {
            return false;
        }
    }

}
