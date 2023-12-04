package io.apicurio.registry.util;

import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;


public class WSDLReaderAccessor {

    private static ThreadLocal<WSDLReader> threadLocalWsdlReader = new ThreadLocal<WSDLReader>() {
        @Override
        protected WSDLReader initialValue() {
            WSDLReader wsdlReader = null;
            try {
                WSDLFactory wsdlFactory = WSDLFactory.newInstance();
                wsdlReader = wsdlFactory.newWSDLReader();
            } catch (WSDLException e) {
                throw new RuntimeException(e);
            }
            return wsdlReader;
        }
    };
    
    public static final WSDLReader getWSDLReader() {
        return threadLocalWsdlReader.get();
    }

}
