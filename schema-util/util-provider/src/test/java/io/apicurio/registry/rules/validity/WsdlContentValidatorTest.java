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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;

import java.util.Collections;

/**
 * @author cfoskin@redhat.com
 */
public class WsdlContentValidatorTest extends ArtifactUtilProviderTestBase {
    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle contentA = resourceToContentHandle("wsdl-valid.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentA, Collections.emptyMap());
        ContentHandle contentB = resourceToContentHandle("wsdl-invalid-semantics.wsdl");
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentB, Collections.emptyMap());
    }

    @Test
    public void testinValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("wsdl-invalid-syntax.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("wsdl-valid.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testinValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("wsdl-invalid-semantics.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            //WSDLException faultCode=INVALID_WSDL: Encountered illegal extension element '{http://schemas.xmlsoap.org/wsdl/}element' in the context of a 'javax.wsdl.Types'. Extension elements must be in a namespace other than WSDL's
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }
}
