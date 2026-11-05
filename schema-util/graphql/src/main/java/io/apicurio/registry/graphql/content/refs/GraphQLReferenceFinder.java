package io.apicurio.registry.graphql.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.graphql.content.GraphQLImportLines;
import io.apicurio.registry.graphql.content.GraphQLImportLines.BlockStringTracker;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;

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
 * not currently tracked by this finder; only the imported file is recorded as a reference). Note
 * that {@link io.apicurio.registry.graphql.content.dereference.GraphQLContentDereferencer} does not
 * resolve this named form on DEREFERENCE, since inlining the whole referenced file would risk
 * introducing conflicting or duplicate type definitions; it is still reported here so the reference
 * is visible to the registry (e.g. for REWRITE, or for a future smarter dereferencer).</li>
 * </ul>
 * Lines that appear inside a GraphQL block-string description (<code>"""..."""</code>) are never
 * treated as import directives, even if their text happens to match the <code>#import</code> syntax.
 * This is a best-effort convention, not a standard. If the content does not use it, no references
 * will be found.
 */
public class GraphQLReferenceFinder implements ReferenceFinder {

    /**
     * @see ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        Set<ExternalReference> refs = new LinkedHashSet<>();
        String raw = content.getContent().content();
        BlockStringTracker blockStringTracker = new BlockStringTracker();
        for (String line : GraphQLImportLines.splitLines(raw)) {
            boolean insideBlockString = blockStringTracker.consume(line);
            if (insideBlockString) {
                continue;
            }
            Matcher matcher = GraphQLImportLines.IMPORT_LINE.matcher(line);
            if (matcher.matches()) {
                refs.add(new ExternalReference(matcher.group(2)));
            }
        }
        return refs;
    }
}
