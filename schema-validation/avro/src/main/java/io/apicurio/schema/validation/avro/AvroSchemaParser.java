/*
 * Copyright 2026 Red Hat
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

package io.apicurio.schema.validation.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.util.Map;
import java.util.stream.Collectors;

public class AvroSchemaParser implements SchemaParser<Schema, GenericRecord> {

    @Override
    public String artifactType() {
        return ArtifactType.AVRO;
    }

    @Override
    public Schema parseSchema(byte[] rawSchema, Map<String, ParsedSchema<Schema>> resolvedReferences) {
        Schema.Parser parser = new Schema.Parser();
        resolveReferences(resolvedReferences, parser);
        return parser.parse(IoUtil.toString(rawSchema));
    }

    private void resolveReferences(Map<String, ParsedSchema<Schema>> resolvedReferences, Schema.Parser parser) {
        resolvedReferences.forEach((referenceName, schema) -> {
            if (schema.hasReferences()) {
                resolveReferences(schema.getSchemaReferences()
                        .stream()
                        .collect(Collectors.toMap(ParsedSchema::referenceName, ps -> ps)), parser);
            }
            parser.parse(IoUtil.toString(schema.getRawSchema()));
        });
    }

    @Override
    public ParsedSchema<Schema> getSchemaFromData(Record<GenericRecord> data) {
        return null;
    }

    @Override
    public ParsedSchema<Schema> getSchemaFromData(Record<GenericRecord> record, boolean dereference) {
        return null;
    }

    @Override
    public boolean supportsExtractSchemaFromData() {
        return false;
    }
}
