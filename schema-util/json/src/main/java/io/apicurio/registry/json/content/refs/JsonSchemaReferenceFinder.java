package io.apicurio.registry.json.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.content.refs.ReferenceFinder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A JSON Schema implementation of a reference finder.
 */
public class JsonSchemaReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            JsonNode tree = mapper.readTree(content.getContent().content());
            // Reference names are the literal $ref values, since that is what the dereferencer and the
            // rewriter match on. References registered under the $id-resolved URI are matched as well, see
            // JsonSchemaRefScopes.
            return JsonSchemaRefScopes.find(tree).stream()
                    .map(site -> new JsonPointerExternalReference(site.ref()))
                    .filter(ref -> ref.getResource() != null).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new ReferenceFinderException("Error finding external references in a JSON Schema file.", e);
        }
    }

}
