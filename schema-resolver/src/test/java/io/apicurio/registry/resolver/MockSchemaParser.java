package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Record;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
