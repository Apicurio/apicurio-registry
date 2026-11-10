package io.apicurio.registry.graphql.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.graphql.content.GraphQLImportLines;
import io.apicurio.registry.graphql.content.GraphQLImportLines.BlockStringTracker;

import java.util.Map;

/**
 * Reference handling for GraphQL SDL content that uses the <code>#import</code> leading-comment
 * convention (see {@link io.apicurio.registry.graphql.content.refs.GraphQLReferenceFinder}).
 * <p>
 * DEREFERENCE replaces each whole-file <code>#import "file.graphql"</code> line with the full
 * resolved content of the referenced document. The named-import form
 * (<code># import Foo, Bar from "file.graphql"</code>) is intentionally <b>not</b> resolved on
 * DEREFERENCE: inlining the entire referenced file would ignore the requested name list and could
 * introduce conflicting or duplicate type definitions (e.g. if only one type was requested, or if
 * the same file is imported more than once under different name lists), producing invalid SDL. Such
 * lines are left unchanged. REWRITE replaces the quoted file name with the resolved Registry API URL
 * for both forms, leaving the import directive otherwise intact, since swapping the file name/URL
 * does not carry the same risk of duplicate definitions.
 * <p>
 * Lines inside a GraphQL block-string description (<code>"""..."""</code>) are never treated as
 * import directives, even if their text happens to match the <code>#import</code> syntax.
 */
public class GraphQLContentDereferencer implements ContentDereferencer {

    /**
     * @see ContentDereferencer#dereference(TypedContent, Map)
     */
    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        if (resolvedReferences == null || resolvedReferences.isEmpty()) {
            return content;
        }
        String original = content.getContent().content();
        StringBuilder result = new StringBuilder();
        boolean changed = false;
        BlockStringTracker blockStringTracker = new BlockStringTracker();
        for (String line : GraphQLImportLines.splitLines(original)) {
            boolean insideBlockString = blockStringTracker.consume(line);
            if (!insideBlockString) {
                GraphQLImportLines.ImportLine importLine = GraphQLImportLines.parse(line);
                if (importLine != null && !importLine.named()) {
                    TypedContent resolved = resolvedReferences.get(importLine.file());
                    if (resolved != null) {
                        result.append(resolved.getContent().content()).append('\n');
                        changed = true;
                        continue;
                    }
                }
            }
            result.append(line).append('\n');
        }
        if (!changed) {
            return content;
        }
        if (result.length() > 0) {
            result.setLength(result.length() - 1);
        }
        return TypedContent.create(ContentHandle.create(result.toString()), content.getContentType());
    }

    /**
     * @see ContentDereferencer#rewriteReferences(TypedContent, Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        if (resolvedReferenceUrls == null || resolvedReferenceUrls.isEmpty()) {
            return content;
        }
        String original = content.getContent().content();
        StringBuilder result = new StringBuilder();
        boolean changed = false;
        BlockStringTracker blockStringTracker = new BlockStringTracker();
        for (String line : GraphQLImportLines.splitLines(original)) {
            boolean insideBlockString = blockStringTracker.consume(line);
            if (!insideBlockString) {
                GraphQLImportLines.ImportLine importLine = GraphQLImportLines.parse(line);
                if (importLine != null) {
                    String url = resolvedReferenceUrls.get(importLine.file());
                    if (url != null) {
                        result.append(importLine.prefix()).append(url).append(importLine.suffix()).append('\n');
                        changed = true;
                        continue;
                    }
                }
            }
            result.append(line).append('\n');
        }
        if (!changed) {
            return content;
        }
        if (result.length() > 0) {
            result.setLength(result.length() - 1);
        }
        return TypedContent.create(ContentHandle.create(result.toString()), content.getContentType());
    }
}
