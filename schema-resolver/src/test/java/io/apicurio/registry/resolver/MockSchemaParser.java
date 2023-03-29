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
package io.apicurio.registry.resolver;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.apicurio.registry.resolver.data.Record;

public class MockSchemaParser implements SchemaParser<String, String> {
    private ParsedSchema<String> dataSchema;
    private String parsedSchema;

    public MockSchemaParser(ParsedSchema<String> dataSchema) {
        this.dataSchema = dataSchema;
    }

    public MockSchemaParser() {
        this(null);
    }

    @Override
    public String artifactType() {
        return "Mock";
    }

    @Override
    public String parseSchema(byte[] rawSchema, Map<String, ParsedSchema<String>> resolvedReferences) {
        this.parsedSchema = new String(rawSchema, StandardCharsets.UTF_8);
        return this.parsedSchema;
    }

    @Override
    public ParsedSchema<String> getSchemaFromData(Record<String> data) {
        if (dataSchema != null) {
            return dataSchema;
        }
        throw new UnsupportedOperationException("get schema from data isn't supported");
    }

    @Override
    public ParsedSchema<String> getSchemaFromData(Record<String> data, boolean dereference) {
        if (dataSchema != null) {
            return dataSchema;
        }
        throw new UnsupportedOperationException("get schema from data isn't supported");
    }

    @Override
    public boolean supportsExtractSchemaFromData() {
        return dataSchema != null;
    }
}
