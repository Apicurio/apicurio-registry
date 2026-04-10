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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides validation APIs for JSON objects (Java objects, byte[], StringJSONObject,...) against a JSON Schema.
 * Schemas are managed in Apicurio Registry and downloaded and cached at runtime by this library.
 *
 * @author Fabian Martinez
 */
public class JsonValidator {

    private SchemaResolver<JsonSchema, Object> schemaResolver;
    private ArtifactReference artifactReference;

    static final ObjectMapper mapper = new ObjectMapper();

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
    public JsonValidationResult validateByArtifactReference(Object bean) {
        Objects.requireNonNull(this.artifactReference, "ArtifactReference must be provided when creating JsonValidator in order to use this feature");
        try {
            SchemaLookupResult<JsonSchema> schema = this.schemaResolver.resolveSchemaByArtifactReference(this.artifactReference);
            JsonNode jsonPayload = createJSONObject(bean);
            return validate(schema.getParsedSchema().getParsedSchema(), jsonPayload);
        } catch (Exception e) {
            return JsonValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    /**
     * Validates the payload of the provided Record against a JSON Schema.
     * This method will resolve the schema based on the configuration provided in the constructor. See {@link SchemaResolverConfig} for configuration options and features of {@link SchemaResolver}.
     * You can use {@link JsonRecord} as the implementation for the provided record or you can use an implementation of your own.
     * Opposite to {@link JsonValidator#validateByArtifactReference(Object)} this method allow to dynamically use a different schema for validating each record.
     *
     * @param record , the record used to resolve the schema used for validation and to provide the payload to validate.
     * @return JsonValidationResult
     */
    public JsonValidationResult validate(Record<Object> record) {
        try {
            SchemaLookupResult<JsonSchema> schema = this.schemaResolver.resolveSchema(record);
            JsonNode jsonPayload = createJSONObject(record.payload());
            return validate(schema.getParsedSchema().getParsedSchema(), jsonPayload);
        } catch (Exception e) {
            return JsonValidationResult.fromErrors(List.of(
                new ValidationError("Failed to resolve schema from registry: " + extractErrorMessage(e), "SCHEMA_RESOLUTION_ERROR")
            ));
        }
    }

    protected JsonValidationResult validate(JsonSchema schema, JsonNode jsonPayload) {
        Set<ValidationMessage> validate = schema.validate(jsonPayload);

        if (!validate.isEmpty()) {
            return JsonValidationResult.fromErrors(extractValidationErrors(validate));
        }

        return JsonValidationResult.SUCCESS;
    }

    private JsonNode createJSONObject(Object bean) {
        if (bean instanceof ByteBuffer) {
            try (InputStream inputStream = new ByteBufferInputStream((ByteBuffer) bean);
                    JsonParser parser = mapper.getFactory().createParser(inputStream)) {
                return parser.readValueAs(JsonNode.class);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            return mapper.convertValue(bean, JsonNode.class);
        }
    }

    private List<ValidationError> extractValidationErrors(Set<ValidationMessage> validationErrors) {
        List<ValidationError> errors = new ArrayList<>();
        if (validationErrors != null && !validationErrors.isEmpty()) {
            for (ValidationMessage cause : validationErrors) {
                ValidationError error = new ValidationError(cause.getMessage(), cause.getCode());
                errors.add(error);
            }
        }
        return errors;
    }

    private String extractErrorMessage(Exception e) {
        StringBuilder errorMessage = new StringBuilder();

        // Start with the exception type and message
        errorMessage.append(e.getClass().getSimpleName());
        String message = getDetailedMessage(e);
        if (message != null && !message.isEmpty()) {
            errorMessage.append(": ").append(message);
        }

        // Add cause chain for more context
        Throwable cause = e.getCause();
        while (cause != null) {
            errorMessage.append(" | Caused by: ").append(cause.getClass().getSimpleName());
            String causeMessage = getDetailedMessage(cause);
            if (causeMessage != null && !causeMessage.isEmpty()) {
                errorMessage.append(": ").append(causeMessage);
            }
            cause = cause.getCause();
        }

        return errorMessage.toString();
    }

    private String getDetailedMessage(Throwable throwable) {
        // Special handling for ProblemDetails from Apicurio Registry REST client
        if (throwable instanceof ProblemDetails) {
            String detail = ((ProblemDetails) throwable).getDetail();
            if (detail != null && !detail.isEmpty()) {
                return detail;
            }
        }
        return throwable.getMessage();
    }

    public static class JsonSchemaParser implements SchemaParser<JsonSchema, Object> {
        @Override
        public String artifactType() {
            return ArtifactType.JSON;
        }

        @Override
        public JsonSchema parseSchema(byte[] rawSchema, Map<String, ParsedSchema<JsonSchema>> resolvedReferences) {
            Map<String, String> referenceSchemas = new HashMap<>();

            resolveReferences(resolvedReferences, referenceSchemas);

            JsonSchemaFactory schemaFactory = JsonSchemaFactory
                    .getInstance(SpecVersion.VersionFlag.V7,
                            builder -> builder.schemaLoaders(schemaLoaders -> schemaLoaders.schemas(referenceSchemas)));

            return schemaFactory.getSchema(IoUtil.toString(rawSchema));
        }

        private void resolveReferences(Map<String, ParsedSchema<JsonSchema>> resolvedReferences, Map<String, String> referenceSchemas) {
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
        public ParsedSchema<JsonSchema> getSchemaFromData(Record<Object> data) {
            //not supported yet?
            return null;
        }

        @Override
        public ParsedSchema<JsonSchema> getSchemaFromData(Record<Object> record, boolean dereference) {
            //not supported yet?
            return null;
        }

        @Override
        public boolean supportsExtractSchemaFromData() {
            return false;
        }
    }

}
