package io.apicurio.registry.xsd.util;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

public class SchemaFactoryAccessor {

    private static ThreadLocal<SchemaFactory> threadLocalSchemaFactory = new ThreadLocal<SchemaFactory>() {
        @Override
        protected SchemaFactory initialValue() {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
                throw new RuntimeException(e);
            }
            return factory;
        }
    };

    public static final SchemaFactory getSchemaFactory() {
        return threadLocalSchemaFactory.get();
    }

}
