package io.apicurio.registry.graphql.content.canon;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.SchemaPrinter.Options;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.graphql.content.GraphQLImportLines;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

/**
 * A canonicalizer that handles GraphQL (SDL) formatted content.
 * <p>
 * Content that uses the <code>#import</code> leading-comment convention (see
 * {@link io.apicurio.registry.graphql.content.refs.GraphQLReferenceFinder}) is returned unchanged
 * rather than parsed and reprinted: {@code graphql-java}'s {@link SchemaPrinter} drops all comments,
 * including <code>#import</code> directives, when reprinting a schema. Canonicalizing such content
 * would therefore silently delete its reference-finder-visible import directives - most noticeably
 * when combined with reference rewriting (<code>canonical=true&amp;references=REWRITE</code>), where
 * the freshly rewritten directive would otherwise vanish from the canonical form.
 */
public class GraphQLContentCanonicalizer implements ContentCanonicalizer {

    private static final SchemaParser sparser = new SchemaParser();
    private static final SchemaGenerator schemaGenerator = new SchemaGenerator();
    private static final RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring().build();
    private static final SchemaPrinter printer = new SchemaPrinter(
            Options.defaultOptions().includeDirectives(false));

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        if (usesImportConvention(content.getContent().content())) {
            return content;
        }
        try {
            TypeDefinitionRegistry typeRegistry = sparser.parse(content.getContent().content());
            String canonicalized = printer.print(schemaGenerator.makeExecutableSchema(typeRegistry, wiring));
            return TypedContent.create(ContentHandle.create(canonicalized), ContentTypes.APPLICATION_GRAPHQL);
        } catch (Exception e) {
            // Must not be a GraphQL file
        }
        return content;
    }

    /**
     * Returns {@code true} if any line of the given content (outside of a block-string description)
     * is a <code>#import</code> directive.
     */
    private boolean usesImportConvention(String rawContent) {
        GraphQLImportLines.BlockStringTracker blockStringTracker = new GraphQLImportLines.BlockStringTracker();
        for (String line : GraphQLImportLines.splitLines(rawContent)) {
            boolean insideBlockString = blockStringTracker.consume(line);
            if (!insideBlockString && GraphQLImportLines.IMPORT_LINE.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

}
