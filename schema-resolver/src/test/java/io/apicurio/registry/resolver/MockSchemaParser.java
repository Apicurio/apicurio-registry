package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Record;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MockSchemaParser implements SchemaParser<String, String> {
    private ParsedSchema<String> dataSchema;
    private String parsedSchema;
    private final Map<String, String> namespaces = new HashMap<>();

    public MockSchemaParser(ParsedSchema<String> dataSchema) {
        this.dataSchema = dataSchema;
    }

    public MockSchemaParser() {
        this(null);
    }

    private String artifactType = "Mock";

    /** Sets the artifact type this parser reports. */
    public MockSchemaParser withArtifactType(String artifactType) {
        this.artifactType = artifactType;
        return this;
    }

    @Override
    public String artifactType() {
        return artifactType;
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

    /** Sets the namespace returned for the referenced schema with the given reference name. */
    public MockSchemaParser withNamespace(String referenceName, String namespace) {
        namespaces.put(referenceName, namespace);
        return this;
    }

    @Override
    public String referenceNamespace(ParsedSchema<String> schema) {
        return namespaces.get(schema.referenceName());
    }
}
