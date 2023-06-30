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

package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Fabian Martinez
 * @author Carles Arnal
 */
public class AvroSchemaParser<U> implements SchemaParser<Schema, U> {

    private AvroDatumProvider<U> avroDatumProvider;

    public AvroSchemaParser(AvroDatumProvider<U> avroDatumProvider) {
        this.avroDatumProvider = avroDatumProvider;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public String artifactType() {
        return ArtifactType.AVRO;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public Schema parseSchema(byte[] rawSchema, Map<String, ParsedSchema<Schema>> resolvedReferences) {
        return AvroSchemaUtils.parse(IoUtil.toString(rawSchema), new ArrayList<>(resolvedReferences.values()));
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(Record)
     */
    @Override
    public ParsedSchema<Schema> getSchemaFromData(Record<U> data) {
        Schema schema = avroDatumProvider.toSchema(data.payload());

        final List<ParsedSchema<Schema>> resolvedReferences = handleReferences(schema);

        return new ParsedSchemaImpl<Schema>()
                .setParsedSchema(schema)
                .setReferenceName(schema.getFullName())
                .setSchemaReferences(resolvedReferences)
                .setRawSchema(IoUtil.toBytes(schema.toString(resolvedReferences.stream().map(ParsedSchema::getParsedSchema).collect(Collectors.toSet()), false)));
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(Record, boolean)
     */
    @Override
    public ParsedSchema<Schema> getSchemaFromData(Record<U> data, boolean dereference) {
        if (dereference) {
            Schema schema = avroDatumProvider.toSchema(data.payload());

            return new ParsedSchemaImpl<Schema>()
                    .setParsedSchema(schema)
                    .setReferenceName(schema.getFullName())
                    .setRawSchema(IoUtil.toBytes(schema.toString()));
        } else {
            return getSchemaFromData(data);
        }
    }
    
    private List<ParsedSchema<Schema>> handleReferences(Schema schema) {
        if (schema.getType() == Schema.Type.RECORD) {
            return handleReferencesInRecord(schema);
        } else if (schema.getType() == Schema.Type.UNION) {
            return handleReferencesInUnion(schema);
        } else {
            return Collections.emptyList();
        }
    }

    private List<ParsedSchema<Schema>> handleReferencesInUnion(Schema schema) {
        final List<ParsedSchema<Schema>> schemaReferences = new ArrayList<>();
        List<Schema> types = schema.getTypes();
        for (Schema type : types) {
            if (type.getType() == Schema.Type.RECORD) {
                final List<ParsedSchema<Schema>> parsedSchemas = handleReferencesInRecord(type);

                byte[] rawSchema = IoUtil.toBytes(type.toString(parsedSchemas.stream().map(ParsedSchema::getParsedSchema).collect(Collectors.toSet()), false));

                ParsedSchema<Schema> referencedSchema = new ParsedSchemaImpl<Schema>()
                        .setParsedSchema(type)
                        .setReferenceName(type.getFullName())
                        .setSchemaReferences(parsedSchemas)
                        .setRawSchema(rawSchema);

                schemaReferences.add(referencedSchema);
            } else if (type.getType() == Schema.Type.UNION) {
                final List<ParsedSchema<Schema>> parsedSchemas = handleReferencesInUnion(type);

                byte[] rawSchema = IoUtil.toBytes(type.toString(parsedSchemas.stream().map(ParsedSchema::getParsedSchema).collect(Collectors.toSet()), false));

                ParsedSchema<Schema> referencedSchema = new ParsedSchemaImpl<Schema>()
                        .setParsedSchema(type)
                        .setReferenceName(type.getFullName())
                        .setSchemaReferences(parsedSchemas)
                        .setRawSchema(rawSchema);

                schemaReferences.add(referencedSchema);
            } else if (type.getType() == Schema.Type.ENUM) {
                byte[] rawSchema = IoUtil.toBytes(type.toString());

                ParsedSchema<Schema> referencedSchema = new ParsedSchemaImpl<Schema>()
                        .setParsedSchema(type)
                        .setReferenceName(type.getFullName())
                        .setSchemaReferences(Collections.emptyList())
                        .setRawSchema(rawSchema);

                schemaReferences.add(referencedSchema);
            }
        }
        return schemaReferences;
    }

    private List<ParsedSchema<Schema>> handleReferencesInRecord(Schema schema) {
        final List<ParsedSchema<Schema>> schemaReferences = new ArrayList<>();
        final List<Schema.Field> schemaFields = schema.getFields();
        for (Schema.Field field: schemaFields) {
            if (field.schema().getType() == Schema.Type.RECORD) {
                final List<ParsedSchema<Schema>> parsedSchemas = handleReferencesInRecord(field.schema());

                byte[] rawSchema = IoUtil.toBytes(field.schema().toString(parsedSchemas.stream().map(ParsedSchema::getParsedSchema).collect(Collectors.toSet()), false));

                ParsedSchema<Schema> referencedSchema = new ParsedSchemaImpl<Schema>()
                        .setParsedSchema(field.schema())
                        .setReferenceName(field.schema().getFullName())
                        .setSchemaReferences(parsedSchemas)
                        .setRawSchema(rawSchema);

                schemaReferences.add(referencedSchema);
            } else if (field.schema().getType() == Schema.Type.UNION) {
                final List<ParsedSchema<Schema>> parsedSchemas = handleReferencesInUnion(field.schema());

                byte[] rawSchema = IoUtil.toBytes(field.schema().toString(parsedSchemas.stream().map(ParsedSchema::getParsedSchema).collect(Collectors.toSet()), false));

                ParsedSchema<Schema> referencedSchema = new ParsedSchemaImpl<Schema>()
                        .setParsedSchema(field.schema())
                        .setReferenceName(field.schema().getFullName())
                        .setSchemaReferences(parsedSchemas)
                        .setRawSchema(rawSchema);

                schemaReferences.add(referencedSchema);
            } else if (field.schema().getType() == Schema.Type.ENUM) {
                byte[] rawSchema = IoUtil.toBytes(field.schema().toString());

                ParsedSchema<Schema> referencedSchema = new ParsedSchemaImpl<Schema>()
                        .setParsedSchema(field.schema())
                        .setReferenceName(field.schema().getFullName())
                        .setSchemaReferences(Collections.emptyList())
                        .setRawSchema(rawSchema);

                schemaReferences.add(referencedSchema);
            }
        }
        return schemaReferences;
    }

}
