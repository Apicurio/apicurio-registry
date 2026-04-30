package io.apicurio.registry.graphql.content.canon;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.SchemaPrinter.Options;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizationException;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

/**
 * A canonicalizer that handles GraphQL (SDL) formatted content.
 */
public class GraphQLContentCanonicalizer extends BaseContentCanonicalizer {

    private static final SchemaParser sparser = new SchemaParser();
    private static final SchemaGenerator schemaGenerator = new SchemaGenerator();
    private static final RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring().build();
    private static final SchemaPrinter printer = new SchemaPrinter(
        Options.defaultOptions().includeDirectives(false));

    /**
     * @see BaseContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
            Map<String, TypedContent> refs) throws ContentCanonicalizationException {
        TypeDefinitionRegistry typeRegistry = sparser.parse(content.getContent().content());
        String canonicalized = printer.print(schemaGenerator.makeExecutableSchema(typeRegistry, wiring));
        return TypedContent.create(ContentHandle.create(canonicalized), ContentTypes.APPLICATION_GRAPHQL);
    }
}
