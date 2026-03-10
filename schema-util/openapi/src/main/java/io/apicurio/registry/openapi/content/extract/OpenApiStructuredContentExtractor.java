package io.apicurio.registry.openapi.content.extract;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Extracts structured elements from OpenAPI content for search indexing. Parses the OpenAPI document using
 * the apicurio-data-models library and extracts schemas, paths, operationIds, tags, parameters, security
 * schemes, and servers via a shared {@link StructuredContentVisitor}.
 */
public class OpenApiStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenApiStructuredContentExtractor.class);

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            Document doc = Library.readDocumentFromJSONString(content.content());
            if (!(doc instanceof OpenApiDocument)) {
                return Collections.emptyList();
            }
            StructuredContentVisitor visitor = new StructuredContentVisitor();
            Library.visitTree(doc, visitor, TraverserDirection.down);
            return visitor.getElements();
        } catch (Exception e) {
            log.debug("Failed to extract structured content from OpenAPI: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
