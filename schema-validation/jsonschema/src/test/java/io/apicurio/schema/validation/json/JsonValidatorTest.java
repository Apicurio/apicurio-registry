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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.apicurio.registry.utils.IoUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Fabian Martinez
 */
public class JsonValidatorTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testValidMessage() {
        JsonValidator validator = new JsonValidator();

        JsonNode jsonPayload = createTestMessageBean();

        JsonSchema validSchema = createSchemaFromResource("message.json");

        var result = validator.validate(validSchema, jsonPayload);

        assertTrue(result.success());
        assertNull(result.getValidationErrors());
    }

    @Test
    public void testInvalidMessage() {
        JsonValidator validator = new JsonValidator();

        JsonNode jsonPayload = createTestMessageBean();

        JsonSchema invalidSchema = createSchemaFromResource("message-invalid.json");

        var result = validator.validate(invalidSchema, jsonPayload);

        assertFalse(result.success());
        assertNotNull(result.getValidationErrors());
        assertFalse(result.getValidationErrors().isEmpty());
        assertEquals(1, result.getValidationErrors().size());
    }

    @Test
    public void testInvalidMessageMultipleErrors() {
        JsonValidator validator = new JsonValidator();

        JsonNode jsonPayload = createTestMessageBean();

        JsonSchema invalidSchema = createSchemaFromResource("message-invalid-multi.json");

        var result = validator.validate(invalidSchema, jsonPayload);

        assertFalse(result.success());
        assertNotNull(result.getValidationErrors());
        assertFalse(result.getValidationErrors().isEmpty());
        assertEquals(2, result.getValidationErrors().size());
    }

    private JsonNode createTestMessageBean() {
        TestMessageBean message = new TestMessageBean();
        message.setMessage("hello");
        message.setTime(System.currentTimeMillis());
        return objectMapper.valueToTree(message);
    }

    private JsonSchema createSchemaFromResource(String resource) {
        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        return schemaFactory.getSchema(readResource(resource));
    }

    public static String readResource(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return IoUtil.toString(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}