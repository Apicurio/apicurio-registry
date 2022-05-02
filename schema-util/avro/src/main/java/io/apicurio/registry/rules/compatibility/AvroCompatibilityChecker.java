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

package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.UnprocessableSchemaException;

import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 */
public class AvroCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingSchemaStrings MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedSchemaString MUST NOT be null");

        SchemaValidator schemaValidator = validatorFor(compatibilityLevel);

        if (schemaValidator == null) {
            return CompatibilityExecutionResult.compatible();
        }

        try {
            final List<Schema> existingSchemas = new ArrayList<>();
            existingArtifacts.forEach(contentHandle -> existingSchemas.add(new Schema.Parser().parse(contentHandle.content())));
            Collections.reverse(existingSchemas); // the most recent must come first, i.e. reverse-chronological.
            Schema toValidate = new Schema.Parser().parse(proposedArtifact.content());
            schemaValidator.validate(toValidate, existingSchemas);
            return CompatibilityExecutionResult.compatible();
        } catch (SchemaValidationException e) {
            return CompatibilityExecutionResult.incompatible(e);
        } catch (SchemaParseException | AvroTypeException e) {
            throw new UnprocessableSchemaException(e.getMessage());
        }    }

    private SchemaValidator validatorFor(CompatibilityLevel compatibilityLevel) {
        switch (compatibilityLevel) {
            case BACKWARD:
                return new SchemaValidatorBuilder().canReadStrategy().validateLatest();
            case BACKWARD_TRANSITIVE:
                return new SchemaValidatorBuilder().canReadStrategy().validateAll();
            case FORWARD:
                return new SchemaValidatorBuilder().canBeReadStrategy().validateLatest();
            case FORWARD_TRANSITIVE:
                return new SchemaValidatorBuilder().canBeReadStrategy().validateAll();
            case FULL:
                return new SchemaValidatorBuilder().mutualReadStrategy().validateLatest();
            case FULL_TRANSITIVE:
                return new SchemaValidatorBuilder().mutualReadStrategy().validateAll();
            default:
                return null;
        }

    }
}