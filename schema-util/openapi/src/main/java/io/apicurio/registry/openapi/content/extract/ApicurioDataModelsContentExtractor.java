package io.apicurio.registry.openapi.content.extract;

import io.apitomy.datamodels.Library;
import io.apitomy.datamodels.TraverserDirection;
import io.apitomy.datamodels.models.Document;
import io.apitomy.datamodels.models.Info;
import io.apitomy.datamodels.models.visitors.CombinedVisitorAdapter;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs meta-data extraction for OpenAPI content.
 */
public class ApicurioDataModelsContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            Document openApi = Library.readDocumentFromJSONString(content.content());
            MetaDataVisitor viz = new MetaDataVisitor();
            Library.visitTree(openApi, viz, TraverserDirection.down);

            ExtractedMetaData metaData = null;
            if (viz.name != null || viz.description != null) {
                metaData = new ExtractedMetaData();
            }
            if (viz.name != null) {
                metaData.setName(viz.name);
            }
            if (viz.description != null) {
                metaData.setDescription(viz.description);
            }
            return metaData;
        } catch (Exception e) {
            log.warn("Error extracting metadata from Open/Async API: {}", e.getMessage());
            return null;
        }
    }

    private static class MetaDataVisitor extends CombinedVisitorAdapter {

        String name;
        String description;

        /**
         * @see CombinedVisitorAdapter#visitInfo(Info)
         */
        @Override
        public void visitInfo(Info node) {
            name = node.getTitle();
            description = node.getDescription();
        }

    }
}
