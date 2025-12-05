package io.apicurio.registry.openapi.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.io.IOException;
import java.util.Map;

public class ApicurioDataModelsContentDereferencer implements ContentDereferencer {

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);
            Document doc = Library.readDocument((ObjectNode) node);
            ReferenceInliner inliner = new ReferenceInliner(resolvedReferences);
            Library.visitTree(doc, inliner, TraverserDirection.down);

            // Preserve the original content format (YAML or JSON)
            String dereferencedContent = writeDocumentPreservingFormat(doc, content.getContentType());
            return TypedContent.create(ContentHandle.create(dereferencedContent), content.getContentType());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.TypedContent,
     *      java.util.Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);
            Document doc = Library.readDocument((ObjectNode) node);
            ReferenceRewriter visitor = new ReferenceRewriter(resolvedReferenceUrls);
            Library.visitTree(doc, visitor, TraverserDirection.down);

            // Preserve the original content format (YAML or JSON)
            String rewrittenContent = writeDocumentPreservingFormat(doc, content.getContentType());
            return TypedContent.create(ContentHandle.create(rewrittenContent), content.getContentType());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes a document to a string, preserving the original content format (YAML or JSON).
     *
     * @param doc the document to write
     * @param originalContentType the original content type
     * @return the document as a string in the appropriate format
     * @throws IOException if an error occurs during conversion
     */
    private String writeDocumentPreservingFormat(Document doc, String originalContentType)
            throws IOException {
        // Convert document to JSON string first (this is what the Library supports)
        String jsonString = Library.writeDocumentToJSONString(doc);

        // If the original content was YAML, convert the JSON back to YAML
        if (isYamlContentType(originalContentType)) {
            ObjectMapper jsonMapper = new ObjectMapper();
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            JsonNode jsonNode = jsonMapper.readTree(jsonString);
            return yamlMapper.writeValueAsString(jsonNode);
        }

        // Otherwise, return as JSON
        return jsonString;
    }

    /**
     * Determines if the given content type represents YAML format.
     *
     * @param contentType the content type to check
     * @return true if the content type is YAML, false otherwise
     */
    private boolean isYamlContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lowerContentType = contentType.toLowerCase();
        return lowerContentType.contains("yaml") || lowerContentType.contains("yml");
    }
}
