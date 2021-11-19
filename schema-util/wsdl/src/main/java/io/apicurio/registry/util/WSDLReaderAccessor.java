/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.util;

import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;

/**
 * @author eric.wittmann@gmail.com
 */
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
