package io.apicurio.registry.maven;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

public interface ParsedDirectoryWrapper<Schema> {

    public Schema getSchema();

    public Map<String, ContentHandle> getSchemaContents();
}
