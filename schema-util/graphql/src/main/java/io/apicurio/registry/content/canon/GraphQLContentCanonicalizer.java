package io.apicurio.registry.content.canon;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.SchemaPrinter.Options;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

/**
 * A canonicalizer that handles GraphQL (SDL) formatted content.
 */
public class GraphQLContentCanonicalizer implements ContentCanonicalizer {

    private static final SchemaParser sparser = new SchemaParser();
    private static final SchemaGenerator schemaGenerator = new SchemaGenerator();
    private static final RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring().build();
    private static final SchemaPrinter printer = new SchemaPrinter(
            Options.defaultOptions().includeDirectives(false));

    /**
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            TypeDefinitionRegistry typeRegistry = sparser.parse(content.content());
            String canonicalized = printer.print(schemaGenerator.makeExecutableSchema(typeRegistry, wiring));
            return ContentHandle.create(canonicalized);
        } catch (Exception e) {
            // Must not be a GraphQL file
        }
        return content;
    }

}
