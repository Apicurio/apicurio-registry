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
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import io.apicurio.registry.content.ContentHandle;

/**
 * @author cfoskin@redhat.com
 */
@ApplicationScoped
public class WsdlContentValidator implements ContentValidator {
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(io.apicurio.registry.rules.validity.ValidityLevel,
     *      io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent) throws InvalidContentException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = artifactContent.stream()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                // just try to parse it
                Document wsdlDoc = builder.parse(stream);
                if (level == ValidityLevel.FULL) {
                    // validate that its a valid schema
                    WSDLFactory wsdlFactory = WSDLFactory.newInstance();
                    WSDLReader wsdlReader = wsdlFactory.newWSDLReader();
                    wsdlReader.readWSDL(null, wsdlDoc);
                }
            } catch (Exception e) {
                throw new InvalidContentException("Syntax violation for WSDL Schema artifact.", e);
            }
        }
    }
}
