package io.apicurio.registry.protobuf.content.dereference;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchemaUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dereferencer for Protobuf schemas.
 *
 * <p>Handles dereferencing (compiling with dependencies) and reference rewriting
 * for protobuf schemas.</p>
 */
public class ProtobufDereferencer implements ContentDereferencer {

    // Pattern to match import statements in protobuf schemas
    // Handles: import "path"; import 'path'; import public "path"; import weak "path";
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(import\\s+(?:public\\s+|weak\\s+)?)([\"'])([^\"']+)([\"'])",
            Pattern.MULTILINE
    );

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        // Build dependencies map from resolved references
        final Map<String, String> schemaDefs = Collections
                .unmodifiableMap(resolvedReferences.entrySet().stream().collect(
                        Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getContent().content())));

        try {
            // Use protobuf4j to compile the schema with all dependencies
            Descriptors.FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
                    "schema.proto", content.getContent().content(), schemaDefs);

            // Convert to FileDescriptorProto (binary representation)
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            fileDescriptorProto.writeTo(outputStream);

            // Dereference returns the whole file descriptor bytes representing the main protobuf schema with the
            // required dependencies.
            return TypedContent.create(ContentHandle.create(outputStream.toByteArray()),
                    ContentTypes.APPLICATION_PROTOBUF);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Rewrite import statements in a protobuf schema to use new reference URLs.
     *
     * <p>This implementation parses the schema text and replaces import paths with
     * the resolved reference URLs provided in the mapping.</p>
     *
     * <p>For example, if the schema contains:</p>
     * <pre>import "common/types.proto";</pre>
     * <p>And the mapping contains:</p>
     * <pre>{"common/types.proto" -> "https://registry.example.com/schemas/types"}</pre>
     * <p>The result will be:</p>
     * <pre>import "https://registry.example.com/schemas/types";</pre>
     *
     * @param content The schema content to rewrite
     * @param resolvedReferenceUrls Map of original import paths to new URLs
     * @return The rewritten schema content with updated import statements
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        if (resolvedReferenceUrls == null || resolvedReferenceUrls.isEmpty()) {
            return content;
        }

        String schemaContent = content.getContent().content();
        String rewrittenContent = rewriteImports(schemaContent, resolvedReferenceUrls);

        // Only create new content if something changed
        if (rewrittenContent.equals(schemaContent)) {
            return content;
        }

        return TypedContent.create(ContentHandle.create(rewrittenContent), content.getContentType());
    }

    /**
     * Rewrite import statements in the schema content.
     *
     * @param schemaContent The original schema content
     * @param referenceMapping Map of original paths to new paths
     * @return The rewritten schema content
     */
    private String rewriteImports(String schemaContent, Map<String, String> referenceMapping) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = IMPORT_PATTERN.matcher(schemaContent);

        while (matcher.find()) {
            String prefix = matcher.group(1);     // "import " or "import public " etc.
            String openQuote = matcher.group(2);   // " or '
            String importPath = matcher.group(3);  // the actual path
            String closeQuote = matcher.group(4);  // " or '

            // Check if this import path should be rewritten
            String newPath = referenceMapping.get(importPath);
            if (newPath != null) {
                // Replace with new path, preserving quote style
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(prefix + openQuote + newPath + closeQuote));
            } else {
                // Keep original
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }
}