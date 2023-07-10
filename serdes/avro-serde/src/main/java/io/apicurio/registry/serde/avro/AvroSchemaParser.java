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

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import org.apache.avro.Schema;

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
        final List<ParsedSchema<Schema>> schemaReferences = new ArrayList<>();
        switch (schema.getType()) {
            case RECORD:
                schemaReferences.addAll(handleRecord(schema));
                break;
            case UNION:
                schemaReferences.addAll(handleUnion(schema));
                break;
            case ENUM:
                schemaReferences.add(handleEnum(schema));
                break;
            case MAP:
                schemaReferences.addAll(handleMap(schema));
                break;
            case ARRAY:
                schemaReferences.addAll(handleArray(schema));
                break;
        }

        return schemaReferences;
    }

    private List<ParsedSchema<Schema>> handleUnion(Schema schema) {
        final List<ParsedSchema<Schema>> schemaReferences = new ArrayList<>();
        for (Schema type : schema.getTypes()) {
            if (isComplexType(type.getType())) {
                addComplexTypeSubSchema(schemaReferences, type);
            }
        }
        return schemaReferences;
    }

    private List<ParsedSchema<Schema>> handleMap(Schema schema) {
        final List<ParsedSchema<Schema>> schemaReferences = new ArrayList<>();
        final Schema elementSchema = schema.getValueType();
        if (isComplexType(schema.getValueType().getType())) {
            addComplexTypeSubSchema(schemaReferences, elementSchema);
        }

        return schemaReferences;
    }

    private List<ParsedSchema<Schema>> handleArray(Schema schema) {
        final List<ParsedSchema<Schema>> schemaReferences = new ArrayList<>();
        final Schema elementSchema = schema.getElementType();
        if (isComplexType(schema.getElementType().getType())) {
            addComplexTypeSubSchema(schemaReferences, elementSchema);
        }

        return schemaReferences;
    }

    private void addComplexTypeSubSchema(List<ParsedSchema<Schema>> schemaReferences, Schema elementSchema) {
        if (elementSchema.getType().equals(Schema.Type.ENUM)) {
            schemaReferences.add(parseSchema(elementSchema, Collections.emptyList()));
        } else if (elementSchema.getType().equals(Schema.Type.RECORD)) {
            List<ParsedSchema<Schema>> nestedReferences = new ArrayList<>(handleReferences(elementSchema));
            schemaReferences.add(parseSchema(elementSchema, nestedReferences));
        }
    }


    private List<ParsedSchema<Schema>> handleRecord(Schema schema) {
        final List<ParsedSchema<Schema>> schemaReferences = new ArrayList<>();
        for (Schema.Field field : schema.getFields()) {
            if (field.schema().getType().equals(Schema.Type.RECORD)) {

                final List<ParsedSchema<Schema>> parsedSchemas = handleReferences(field.schema());

                byte[] rawSchema = IoUtil.toBytes(field.schema().toString(parsedSchemas.stream().map(ParsedSchema::getParsedSchema).collect(Collectors.toSet()), false));

                ParsedSchema<Schema> referencedSchema = new ParsedSchemaImpl<Schema>()
                        .setParsedSchema(field.schema())
                        .setReferenceName(field.schema().getFullName())
                        .setSchemaReferences(parsedSchemas)
                        .setRawSchema(rawSchema);

                schemaReferences.add(referencedSchema);
            } else if (field.schema().getType().equals(Schema.Type.UNION)) {
                schemaReferences.addAll(handleUnion(field.schema()));

            } else if (field.schema().getType().equals(Schema.Type.ARRAY)) {
                schemaReferences.addAll(handleArray(field.schema()));

            } else if (field.schema().getType().equals(Schema.Type.MAP)) {
                schemaReferences.addAll(handleMap(field.schema()));

            } else if (field.schema().getType().equals(Schema.Type.ENUM)) {
                schemaReferences.add(handleEnum(field.schema()));
            }
        }

        return schemaReferences;
    }

    private ParsedSchema<Schema> parseSchema(Schema schema, List<ParsedSchema<Schema>> schemaReferences) {
        byte[] rawSchema = IoUtil.toBytes(schema.toString(schemaReferences.stream().map(ParsedSchema::getParsedSchema).collect(Collectors.toSet()), false));

        return new ParsedSchemaImpl<Schema>()
                .setParsedSchema(schema)
                .setReferenceName(schema.getFullName())
                .setSchemaReferences(schemaReferences)
                .setRawSchema(rawSchema);
    }

    private ParsedSchema<Schema> handleEnum(Schema schema) {
        byte[] rawSchema = IoUtil.toBytes(schema.toString());

        return new ParsedSchemaImpl<Schema>()
                .setParsedSchema(schema)
                .setReferenceName(schema.getFullName())
                .setSchemaReferences(Collections.emptyList())
                .setRawSchema(rawSchema);
    }

    public boolean isComplexType(Schema.Type type) {
        return type == Schema.Type.ARRAY || type == Schema.Type.MAP || type == Schema.Type.RECORD || type == Schema.Type.ENUM || type == Schema.Type.UNION;
    }
}
