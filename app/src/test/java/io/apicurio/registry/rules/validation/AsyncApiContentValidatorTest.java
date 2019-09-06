/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rules.validation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractRegistryTest;

/**
 * Tests the AsyncAPI content validator.
 * @author eric.wittmann@gmail.com
 */
public class AsyncApiContentValidatorTest extends AbstractRegistryTest {

    @Test
    public void testValidSyntax() throws Exception {
        String content = resourceToString("asyncapi-valid-syntax.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        validator.validate(ValidationLevel.SYNTAX_ONLY, content);
    }

    @Test
    public void testValidSemantics() throws Exception {
        String content = resourceToString("asyncapi-valid-semantics.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        validator.validate(ValidationLevel.FULL, content);
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        String content = resourceToString("asyncapi-invalid-syntax.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        Assertions.assertThrows(InvalidContentException.class, () -> {
            validator.validate(ValidationLevel.SYNTAX_ONLY, content);
        });
    }

    @Test
    public void testInvalidSemantics() throws Exception {
        String content = resourceToString("asyncapi-invalid-semantics.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        Assertions.assertThrows(InvalidContentException.class, () -> {
            validator.validate(ValidationLevel.FULL, content);
        });
    }

}
