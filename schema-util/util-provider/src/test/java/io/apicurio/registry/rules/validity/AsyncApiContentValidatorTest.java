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

package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests the AsyncAPI content validator.
 * @author eric.wittmann@gmail.com
 */
public class AsyncApiContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-valid-syntax.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-valid-semantics.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-invalid-syntax.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testInvalidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-invalid-semantics.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

}
