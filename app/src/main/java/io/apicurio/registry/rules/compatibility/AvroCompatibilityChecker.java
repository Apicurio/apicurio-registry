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

import org.apache.avro.Schema;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 */
public class AvroCompatibilityChecker implements CompatibilityChecker {

    /**
     * @see CompatibilityChecker#testCompatibility(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<String> existingSchemaStrings, String proposedSchemaString) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingSchemaStrings, "existingSchemaStrings MUST NOT be null");
        requireNonNull(proposedSchemaString, "proposedSchemaString MUST NOT be null");

        SchemaValidator schemaValidator = validatorFor(compatibilityLevel);

        if (schemaValidator == null) {
            return CompatibilityExecutionResult.compatible();
        }

        List<Schema> existingSchemas = existingSchemaStrings.stream().map(s -> new Schema.Parser().parse(s)).collect(Collectors.toList());
        Collections.reverse(existingSchemas); // the most recent must come first, i.e. reverse-chronological.
        Schema toValidate = new Schema.Parser().parse(proposedSchemaString);

        try {
            schemaValidator.validate(toValidate, existingSchemas);
            return CompatibilityExecutionResult.compatible();
        } catch (SchemaValidationException e) {
            return CompatibilityExecutionResult.incompatible(e);
        }
    }

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