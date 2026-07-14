package io.apicurio.registry.thrift.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

public class ThriftContentCanonicalizer implements ContentCanonicalizer {

    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String raw = content.getContent().content();
            String stripped = ThriftIdlParser.stripComments(raw);
            String canonicalized = normalizeWhitespace(stripped);
            return TypedContent.create(ContentHandle.create(canonicalized), ContentTypes.APPLICATION_THRIFT);
        } catch (Exception e) {
            return content;
        }
    }

    private String normalizeWhitespace(String content) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\\r?\\n");
        boolean previousBlank = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!previousBlank && !result.isEmpty()) {
                    result.append('\n');
                    previousBlank = true;
                }
            } else {
                result.append(trimmed).append('\n');
                previousBlank = false;
            }
        }

        return result.toString().trim() + "\n";
    }

}
