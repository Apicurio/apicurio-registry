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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.content.ContentHandle;

/**
 * @author cfoskin@redhat.com
 */
public class XmlContentValidatorTest extends AbstractRegistryTestBase {
    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("xml-valid.xml");
        XsdContentValidator validator = new XsdContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content);
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("xml-invalid-syntax.xml");
        XsdContentValidator validator = new XsdContentValidator();
        Assertions.assertThrows(InvalidContentException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content);
        });
    }
}
