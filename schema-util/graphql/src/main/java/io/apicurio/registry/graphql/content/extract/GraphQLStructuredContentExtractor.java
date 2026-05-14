package io.apicurio.registry.graphql.content.extract;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Extracts structured elements from GraphQL SDL content for search indexing. Parses the GraphQL schema and
 * extracts object type names, field names, enum names, input type names, interface names, union names, and
 * directive names.
 */
public class GraphQLStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(GraphQLStructuredContentExtractor.class);

    private static final Set<String> ROOT_TYPES = Set.of("Query", "Mutation", "Subscription");

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            SchemaParser parser = new SchemaParser();
            TypeDefinitionRegistry registry = parser.parse(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractObjectTypes(registry, elements);
            extractEnumTypes(registry, elements);
            extractInputTypes(registry, elements);
            extractInterfaceTypes(registry, elements);
            extractUnionTypes(registry, elements);
            extractDirectives(registry, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from GraphQL schema: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts object type names and their field names, excluding built-in root types.
     */
    private void extractObjectTypes(TypeDefinitionRegistry registry, List<StructuredElement> elements) {
        registry.types().values().stream()
                .filter(ObjectTypeDefinition.class::isInstance)
                .map(ObjectTypeDefinition.class::cast)
                .filter(type -> !ROOT_TYPES.contains(type.getName()))
                .forEach(type -> {
                    elements.add(new StructuredElement("type", type.getName()));
                    for (FieldDefinition field : type.getFieldDefinitions()) {
                        elements.add(new StructuredElement("field", field.getName()));
                    }
                });
    }

    /**
     * Extracts enum type names.
     */
    private void extractEnumTypes(TypeDefinitionRegistry registry, List<StructuredElement> elements) {
        registry.types().values().stream()
                .filter(EnumTypeDefinition.class::isInstance)
                .map(EnumTypeDefinition.class::cast)
                .forEach(type -> elements.add(new StructuredElement("enum", type.getName())));
    }

    /**
     * Extracts input object type names.
     */
    private void extractInputTypes(TypeDefinitionRegistry registry, List<StructuredElement> elements) {
        registry.types().values().stream()
                .filter(InputObjectTypeDefinition.class::isInstance)
                .map(InputObjectTypeDefinition.class::cast)
                .forEach(type -> elements.add(new StructuredElement("input", type.getName())));
    }

    /**
     * Extracts interface type names.
     */
    private void extractInterfaceTypes(TypeDefinitionRegistry registry, List<StructuredElement> elements) {
        registry.types().values().stream()
                .filter(InterfaceTypeDefinition.class::isInstance)
                .map(InterfaceTypeDefinition.class::cast)
                .forEach(type -> elements.add(new StructuredElement("interface", type.getName())));
    }

    /**
     * Extracts union type names.
     */
    private void extractUnionTypes(TypeDefinitionRegistry registry, List<StructuredElement> elements) {
        registry.types().values().stream()
                .filter(UnionTypeDefinition.class::isInstance)
                .map(UnionTypeDefinition.class::cast)
                .forEach(type -> elements.add(new StructuredElement("union", type.getName())));
    }

    /**
     * Extracts directive definition names.
     */
    private void extractDirectives(TypeDefinitionRegistry registry, List<StructuredElement> elements) {
        registry.getDirectiveDefinitions().values().stream()
                .forEach(directive -> elements.add(new StructuredElement("directive", directive.getName())));
    }
}
