package io.apicurio.registry.maven;

import io.apicurio.registry.content.TypedContent;

import java.util.Map;

public interface ParsedDirectoryWrapper<Schema> {

    public Schema getSchema();

    public Map<String, TypedContent> getSchemaContents();
}
