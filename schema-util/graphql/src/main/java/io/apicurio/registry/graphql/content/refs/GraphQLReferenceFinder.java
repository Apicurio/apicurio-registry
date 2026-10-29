package io.apicurio.registry.graphql.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds external references in GraphQL SDL content.
 * <p>
 * The core GraphQL specification has no standard mechanism for importing type definitions from
 * another document, so there is nothing in this codebase's existing GraphQL support (accepter,
 * canonicalizer, validator) to build on for cross-file references. This finder implements the
 * <code>#import</code> leading-comment convention popularized by tools such as
 * <a href="https://github.com/ardatan/graphql-tools">graphql-tools</a> /
 * <a href="https://github.com/ardatan/graphql-import">graphql-import</a>, since it is the most
 * widely recognized real-world convention for this purpose. Two forms are recognized, each on its
 * own line:
 * <ul>
 * <li><code>#import "other.graphql"</code> - import the whole file</li>
 * <li><code># import Foo, Bar from "other.graphql"</code> - import specific types (the type list is
 * not currently tracked by this finder; only the imported file is recorded as a reference)</li>
 * </ul>
 * This is a best-effort convention, not a standard. If the content does not use it, no references
 * will be found.
 */
public class GraphQLReferenceFinder implements ReferenceFinder {

    private static final Pattern IMPORT_PATTERN = Pattern
            .compile("^\\s*#\\s*import\\s+(?:.*?\\s+from\\s+)?\"([^\"]+)\"\\s*$",
                    Pattern.CASE_INSENSITIVE);

    /**
     * @see ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        Set<ExternalReference> refs = new LinkedHashSet<>();
        String raw = content.getContent().content();
        for (String line : raw.split("\\r?\\n")) {
            Matcher matcher = IMPORT_PATTERN.matcher(line);
            if (matcher.matches()) {
                refs.add(new ExternalReference(matcher.group(1)));
            }
        }
        return refs;
    }
}
