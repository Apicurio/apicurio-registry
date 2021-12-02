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

package io.apicurio.registry.rules.compatibility.jsonschema;

import static io.apicurio.registry.rules.compatibility.jsonschema.JsonUtil.MAPPER;
import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

import java.util.Set;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffContext;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.SchemaDiffVisitor;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class JsonSchemaDiffLibrary {

    /**
     * Find and analyze differences between two JSON schemas.
     *
     * @param original Original/Previous/First/Left JSON schema representation
     * @param updated  Updated/Next/Second/Right JSON schema representation
     * @return an object to access the found differences: Original -&gt; Updated
     * @throws IllegalArgumentException if the input is not a valid representation of a JsonSchema
     */
    public static DiffContext findDifferences(String original, String updated) {
        try {
            JSONObject originalJson = MAPPER.readValue(original, JSONObject.class);
            JSONObject updatedJson = MAPPER.readValue(updated, JSONObject.class);

            Schema originalSchema = SchemaLoader.builder()
                    .schemaJson(originalJson)
                    .build().load().build();

            Schema updatedSchema = SchemaLoader.builder().schemaJson(updatedJson).build().load().build();

            return findDifferences(originalSchema, updatedSchema);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DiffContext findDifferences(Schema originalSchema, Schema updatedSchema) {
        DiffContext rootContext = DiffContext.createRootContext();
        new SchemaDiffVisitor(rootContext, originalSchema).visit(wrap(updatedSchema));
        return rootContext;
    }

    public static boolean isCompatible(String original, String updated) {
        return findDifferences(original, updated).foundAllDifferencesAreCompatible();
    }

    public static Set<Difference> getIncompatibleDifferences(String original, String updated) {
        return findDifferences(original, updated).getIncompatibleDifferences();
    }
}