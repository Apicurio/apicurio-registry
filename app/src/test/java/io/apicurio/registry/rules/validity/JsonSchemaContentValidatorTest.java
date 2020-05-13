/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rules.validity;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.content.ContentHandle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the JSON Schema content validator.
 * @author eric.wittmann@gmail.com
 */
public class JsonSchemaContentValidatorTest extends AbstractRegistryTestBase {
    
    @Test
    public void testValidJsonSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("jsonschema-valid.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content);
    }

    @Test
    public void testInvalidJsonSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("jsonschema-invalid.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        Assertions.assertThrows(InvalidContentException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content);
        });
    }

    @Test
    public void testInvalidJsonSchemaVersion() throws Exception {
        ContentHandle content = resourceToContentHandle("jsonschema-valid-d7.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.FULL, content);
    }

}
