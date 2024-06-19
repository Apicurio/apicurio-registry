package io.apicurio.registry.content.extract;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.util.DocumentBuilderAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.io.InputStream;

/**
 * Performs meta-data extraction for WSDL or XSD content.
 */
public class WsdlOrXsdContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try (InputStream contentIS = content.stream()) {
            Document document = DocumentBuilderAccessor.getDocumentBuilder().parse(contentIS);
            String name = document.getDocumentElement().getAttribute("name");
            String targetNS = document.getDocumentElement().getAttribute("targetNamespace");

            ExtractedMetaData metaData = null;
            if (name != null && !name.equals("")) {
                metaData = new ExtractedMetaData();
                metaData.setName(name);
            } else if (targetNS != null && !targetNS.equals("")) {
                metaData = new ExtractedMetaData();
                metaData.setName(targetNS);
            }
            return metaData;
        } catch (Exception e) {
            log.debug("Error extracting metadata from WSDL/XSD: {}", e.getMessage());
            return null;
        }
    }
}
