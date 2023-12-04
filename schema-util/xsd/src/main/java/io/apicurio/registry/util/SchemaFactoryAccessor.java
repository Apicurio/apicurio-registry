package io.apicurio.registry.util;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;


public class SchemaFactoryAccessor {
    
    private static ThreadLocal<SchemaFactory> threadLocalSchemaFactory = new ThreadLocal<SchemaFactory>() {
        @Override
        protected SchemaFactory initialValue() {
                SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                try {
                    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
                    // Don't care.
                }
                return factory;
        }
    };
    
    public static final SchemaFactory getSchemaFactory() {
        return threadLocalSchemaFactory.get();
    }

}
