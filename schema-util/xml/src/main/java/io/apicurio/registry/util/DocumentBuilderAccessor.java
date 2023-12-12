package io.apicurio.registry.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Base class for any class that needs to use a DocumentBuilder.
 */
public final class DocumentBuilderAccessor {

    protected static ThreadLocal<DocumentBuilder> threadLocaldocBuilder = new ThreadLocal<DocumentBuilder>() {
        @Override
        protected DocumentBuilder initialValue() {
            DocumentBuilder builder = null;
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setNamespaceAware(true);
                builder = factory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
            return builder;
        }
    };

    public static DocumentBuilder getDocumentBuilder() {
        return threadLocaldocBuilder.get();
    }

}
