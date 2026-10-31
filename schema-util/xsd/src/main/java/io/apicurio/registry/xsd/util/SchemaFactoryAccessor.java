package io.apicurio.registry.xsd.util;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

public class SchemaFactoryAccessor {

    private static ThreadLocal<SchemaFactory> threadLocalSchemaFactory = new ThreadLocal<SchemaFactory>() {
        @Override
        protected SchemaFactory initialValue() {
            return newSchemaFactory();
        }
    };

    /**
     * Creates a new, hardened schema factory (no external schema/DTD access, secure processing enabled).
     * Use this instead of the shared thread-local factory when the factory must be customized (e.g. with a
     * resource resolver).
     *
     * @return a new schema factory
     */
    public static SchemaFactory newSchemaFactory() {
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

    public static final SchemaFactory getSchemaFactory() {
        return threadLocalSchemaFactory.get();
    }

}
