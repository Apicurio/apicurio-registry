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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import io.apicurio.registry.content.ContentHandle;

/**
 * @author cfoskin@redhat.com This class can be used to validate plain XML and only does syntax validation
 */
@ApplicationScoped
public class XmlContentValidator implements ContentValidator {

    protected static ThreadLocal<DocumentBuilder> threadLocaldocBuilder = new ThreadLocal<DocumentBuilder>() {
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

        @Override
        public DocumentBuilder get() {
            return super.get();
        }
    };

    /**
     * Constructor.
     */
    public XmlContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(io.apicurio.registry.rules.validity.ValidityLevel,
     *      io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent) throws InvalidContentException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = artifactContent.stream()) {
                threadLocaldocBuilder.get().parse(stream);
            } catch (Exception e) {
                throw new InvalidContentException("Syntax violation for XML Schema artifact.", e);
            }
        }
    }

}
