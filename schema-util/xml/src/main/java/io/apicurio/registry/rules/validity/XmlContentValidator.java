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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.DocumentBuilderAccessor;

/**
 * @author cfoskin@redhat.com This class can be used to validate plain XML and only does syntax validation
 */
public class XmlContentValidator implements ContentValidator {

    /**
     * Constructor.
     */
    public XmlContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, java.util.Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = artifactContent.stream()) {
                DocumentBuilderAccessor.getDocumentBuilder().parse(stream);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for XML artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }

}
