package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.refs.IReferenceResolver;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.types.ContentTypes;

import java.io.IOException;
import java.util.Map;

public class ApicurioDataModelsContentDereferencer implements ContentDereferencer {

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);
            Document document = Library.readDocument((ObjectNode) node);
            IReferenceResolver resolver = new RegistryReferenceResolver(resolvedReferences);
            Document dereferencedDoc = Library.dereferenceDocument(document, resolver, false);
            String dereferencedContentStr = Library.writeDocumentToJSONString(dereferencedDoc);
            return TypedContent.create(ContentHandle.create(dereferencedContentStr), ContentTypes.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.TypedContent, java.util.Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        try {
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);
            Document doc = Library.readDocument((ObjectNode) node);
            ReferenceRewriter visitor = new ReferenceRewriter(resolvedReferenceUrls);
            Library.visitTree(doc, visitor, TraverserDirection.down);
            return TypedContent.create(ContentHandle.create(Library.writeDocumentToJSONString(doc)), ContentTypes.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
