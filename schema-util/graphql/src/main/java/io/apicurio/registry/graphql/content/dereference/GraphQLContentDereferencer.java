package io.apicurio.registry.graphql.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference handling for GraphQL SDL content that uses the <code>#import</code> leading-comment
 * convention (see {@link io.apicurio.registry.graphql.content.refs.GraphQLReferenceFinder}).
 * <p>
 * DEREFERENCE replaces each <code>#import "file.graphql"</code> line with the full resolved
 * content of the referenced document. REWRITE replaces the quoted file name with the resolved
 * Registry API URL, leaving the import directive otherwise intact.
 */
public class GraphQLContentDereferencer implements ContentDereferencer {

    private static final Pattern IMPORT_LINE = Pattern
            .compile("^(\\s*#\\s*import\\s+(?:.*?\\s+from\\s+)?\")([^\"]+)(\"\\s*)$",
                    Pattern.CASE_INSENSITIVE);

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
        for (String line : original.split("\\r?\\n", -1)) {
            Matcher matcher = IMPORT_LINE.matcher(line);
            if (matcher.matches()) {
                TypedContent resolved = resolvedReferences.get(matcher.group(2));
                if (resolved != null) {
                    result.append(resolved.getContent().content()).append('\n');
                    changed = true;
                    continue;
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
        for (String line : original.split("\\r?\\n", -1)) {
            Matcher matcher = IMPORT_LINE.matcher(line);
            if (matcher.matches()) {
                String url = resolvedReferenceUrls.get(matcher.group(2));
                if (url != null) {
                    result.append(matcher.group(1)).append(url).append(matcher.group(3)).append('\n');
                    changed = true;
                    continue;
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
