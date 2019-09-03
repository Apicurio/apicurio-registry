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

import org.junit.jupiter.api.Test;

/**
 * @author eric.wittmann@gmail.com
 */
public class ValidationRuleTest {

    private static final String EMPTY_API_CONTENT = "{\r\n" + 
            "    \"openapi\": \"3.0.2\",\r\n" + 
            "    \"info\": {\r\n" + 
            "        \"title\": \"Empty API\",\r\n" + 
            "        \"version\": \"1.0.0\",\r\n" + 
            "        \"description\": \"An example API design using OpenAPI.\"\r\n" + 
            "    }\r\n" + 
            "}";

    @Test
    public void testOpenApiValidation() throws Exception {
        OpenApiContentValidator validator = new OpenApiContentValidator();
        validator.validate(ValidationLevel.SYNTAX_ONLY, EMPTY_API_CONTENT);
    }
    
    // TODO add many more tests

}
