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
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import io.apicurio.registry.content.ContentHandle;

/**
 * @author cfoskin@redhat.com
 */
@ApplicationScoped
public class XsdContentValidator extends XmlContentValidator {
    private static ThreadLocal<SchemaFactory> threadLocalSchemaFactory = new ThreadLocal<SchemaFactory>() {
        @Override
        protected SchemaFactory initialValue() {
            return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        }
    };

    /**
     * Constructor.
     */
    public XsdContentValidator() {
    }
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(io.apicurio.registry.rules.validity.ValidityLevel,
     *      io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent) throws InvalidContentException {
        super.validate(level, artifactContent);

        if (level == ValidityLevel.FULL) {
            try (InputStream semanticStream = artifactContent.stream()) {
                // validate that its a valid schema
                Source source = new StreamSource(semanticStream);
                threadLocalSchemaFactory.get().newSchema(source);
            } catch (Exception e) {
                throw new InvalidContentException("Syntax violation for XSD Schema artifact.", e);
            }
        }
    }
}
