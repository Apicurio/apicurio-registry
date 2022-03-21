/*
 * Copyright 2020 Red Hat Inc
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
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.SchemaFactoryAccessor;

/**
 * @author cfoskin@redhat.com
 */
public class XsdContentValidator extends XmlContentValidator {

    /**
     * Constructor.
     */
    public XsdContentValidator() {
    }
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        super.validate(level, artifactContent, resolvedReferences);

        if (level == ValidityLevel.FULL) {
            try (InputStream semanticStream = artifactContent.stream()) {
                // validate that its a valid schema
                Source source = new StreamSource(semanticStream);
                SchemaFactoryAccessor.getSchemaFactory().newSchema(source);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for XSD Schema artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }
}
