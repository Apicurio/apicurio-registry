/*
 * Copyright 2020 JBoss Inc
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

package io.apicurio.registry.rules.validity;

import java.io.InputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import io.apicurio.registry.content.ContentHandle;

/**
 * @author cfoskin@redhat.com
 */
@ApplicationScoped
public class WsdlContentValidator implements ContentValidator {

    private static ThreadLocal<DocumentBuilder> threadLocaldocBuilder = new ThreadLocal<DocumentBuilder>() {
        @Override
        protected DocumentBuilder initialValue() {
            DocumentBuilder builder = null;
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                builder = factory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
            return builder;
        }

        public DocumentBuilder get() {
            return super.get();
        }
    };

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

        public WSDLReader get() {
            return super.get();
        }
    };

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(io.apicurio.registry.rules.validity.ValidityLevel,
     *      io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent) throws InvalidContentException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = artifactContent.stream()) {
                // just try to parse it
                Document wsdlDoc = threadLocaldocBuilder.get().parse(stream);
                if (level == ValidityLevel.FULL) {
                    // validate that its a valid schema
                    threadLocalWsdlReader.get().readWSDL(null, wsdlDoc);
                }
            } catch (Exception e) {
                throw new InvalidContentException("Syntax violation for WSDL Schema artifact.", e);
            }
        }
    }
}
