/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.resolver.ParsedSchema;

import java.io.IOException;
/**
 * @author Carles Arnal
*/
public class JsonSchemaValidationUtil {

    /**
     * @param schema the schema to test the data.
     * @param data the data to test.
     * @param mapper the object mapper to be used to read the data.
     * @throws IOException In case of validation errors, a IO exception is thrown.
     */
    protected static void validateDataWithSchema(ParsedSchema<JsonSchema> schema, byte[] data, ObjectMapper mapper) throws IOException {
        schema.getParsedSchema().validate(mapper.readTree(data));
    }
}
