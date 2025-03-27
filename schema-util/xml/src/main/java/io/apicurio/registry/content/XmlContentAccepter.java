package io.apicurio.registry.content;

import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.util.DocumentBuilderAccessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Map;

public class XmlContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType.toLowerCase().contains("xml")
                    && ContentTypeUtil.isParsableXml(content.getContent())) {
                Document xmlDocument = DocumentBuilderAccessor.getDocumentBuilder()
                        .parse(content.getContent().stream());
                Element root = xmlDocument.getDocumentElement();
                String ns = root.getNamespaceURI();
                if (ns != null && ns.equals("http://www.w3.org/2001/XMLSchema")) {
                    return false;
                } else if (ns != null && (ns.equals("http://schemas.xmlsoap.org/wsdl/")
                        || ns.equals("http://www.w3.org/ns/wsdl/"))) {
                    return false;
                } else {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

}
