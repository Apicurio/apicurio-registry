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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the OpenAPI content validator.
 * @author eric.wittmann@gmail.com
 */
public class OpenApiContentValidatorTest {

    private static final String resourceToString(String resourceName)  {
        InputStream stream = OpenApiContentValidatorTest.class.getResourceAsStream(resourceName);
        return new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
    }
    
    @Test
    public void testValidSyntax() throws Exception {
        String content = resourceToString("openapi-valid-syntax.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        validator.validate(ValidationLevel.SYNTAX_ONLY, content);
    }

    @Test
    public void testValidSemantics() throws Exception {
        String content = resourceToString("openapi-valid-semantics.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        validator.validate(ValidationLevel.FULL, content);
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        String content = resourceToString("openapi-invalid-syntax.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        Assertions.assertThrows(InvalidContentException.class, () -> {
            validator.validate(ValidationLevel.SYNTAX_ONLY, content);
        });
    }

    @Test
    public void testInvalidSemantics() throws Exception {
        String content = resourceToString("openapi-invalid-semantics.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        Assertions.assertThrows(InvalidContentException.class, () -> {
            validator.validate(ValidationLevel.FULL, content);
        });
    }

}
