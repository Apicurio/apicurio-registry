/*
 * Copyright 2026 The Apicurio Registry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonSchemaRemoteReferenceTest {

    private static final String EXISTING_SCHEMA = """
            {
              "$id": "https://example.com/blank.schema.json",
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {}
            }
            """;

    private TypedContent toTypedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testUnresolvedRemoteReferenceIsNotFetched() throws Exception {
        JsonSchemaCompatibilityChecker checker = new JsonSchemaCompatibilityChecker();
        String proposedSchema = """
                {
                  "$id": "https://example.com/blank.schema.json",
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "x": {
                      "$ref": "http://127.0.0.1:1/"
                    }
                  }
                }
                """;

        assertDoesNotThrow(() -> checker.testCompatibility(CompatibilityLevel.BACKWARD,
                Collections.singletonList(toTypedContent(EXISTING_SCHEMA)),
                toTypedContent(proposedSchema), Collections.emptyMap()));
    }

    @Test
    void testResolvedRemoteReferenceContentIsUsed() {
        JsonSchemaCompatibilityChecker checker = new JsonSchemaCompatibilityChecker();
        String existingSchema = """
                {
                  "$id": "https://example.com/schemas/root.json",
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "x": {
                      "type": "integer"
                    }
                  }
                }
                """;
        String proposedSchema = """
                {
                  "$id": "https://example.com/schemas/root.json",
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "x": {
                      "$ref": "address.json"
                    }
                  }
                }
                """;

        var result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                Collections.singletonList(toTypedContent(existingSchema)),
                toTypedContent(proposedSchema),
                Collections.singletonMap("address.json", toTypedContent("""
                        {
                          "type": "string"
                        }
                        """)));

        assertFalse(result.isCompatible(), "Supplied referenced content should be used during compatibility checking");
    }
}
