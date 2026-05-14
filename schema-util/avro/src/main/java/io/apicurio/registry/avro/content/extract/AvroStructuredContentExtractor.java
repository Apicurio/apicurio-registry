package io.apicurio.registry.avro.content.extract;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts structured elements from Avro schema content for search indexing. Parses the Avro schema and
 * extracts the schema name, namespace, field names (recursively), named type references, and enum symbols.
 */
public class AvroStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(AvroStructuredContentExtractor.class);

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            Schema.Parser parser = new Schema.Parser();
            Schema schema = parser.parse(content.content());
            List<StructuredElement> elements = new ArrayList<>();
            Set<String> visited = new HashSet<>();

            extractFromSchema(schema, elements, visited);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from Avro schema: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Recursively extracts structured elements from an Avro schema.
     *
     * @param schema the Avro schema to extract from
     * @param elements the list to add extracted elements to
     * @param visited set of already-visited schema full names to avoid infinite recursion
     */
    private void extractFromSchema(Schema schema, List<StructuredElement> elements,
            Set<String> visited) {
        if (schema == null) {
            return;
        }

        switch (schema.getType()) {
        case RECORD:
            String fullName = schema.getFullName();
            if (visited.contains(fullName)) {
                return; // Avoid infinite recursion for self-referencing schemas
            }
            visited.add(fullName);

            elements.add(new StructuredElement("name", schema.getName()));
            extractNamespace(schema, elements);

            // Extract fields and recurse into field types
            for (Schema.Field field : schema.getFields()) {
                elements.add(new StructuredElement("field", field.name()));
                extractFromSchema(field.schema(), elements, visited);
            }
            break;

        case ENUM:
            if (!visited.contains(schema.getFullName())) {
                visited.add(schema.getFullName());
                elements.add(new StructuredElement("type", schema.getName()));
                extractNamespace(schema, elements);
                for (String symbol : schema.getEnumSymbols()) {
                    elements.add(new StructuredElement("enum_symbol", symbol));
                }
            }
            break;

        case FIXED:
            if (!visited.contains(schema.getFullName())) {
                visited.add(schema.getFullName());
                elements.add(new StructuredElement("type", schema.getName()));
                extractNamespace(schema, elements);
            }
            break;

        case ARRAY:
            extractFromSchema(schema.getElementType(), elements, visited);
            break;

        case MAP:
            extractFromSchema(schema.getValueType(), elements, visited);
            break;

        case UNION:
            for (Schema member : schema.getTypes()) {
                extractFromSchema(member, elements, visited);
            }
            break;

        default:
            // Primitive types: no structured elements to extract
            break;
        }
    }

    /**
     * Extracts the namespace from a named schema if present.
     */
    private void extractNamespace(Schema schema, List<StructuredElement> elements) {
        try {
            String namespace = schema.getNamespace();
            if (namespace != null && !namespace.isBlank()) {
                elements.add(new StructuredElement("namespace", namespace));
            }
        } catch (Exception e) {
            // Some schema types don't support namespace
        }
    }
}
