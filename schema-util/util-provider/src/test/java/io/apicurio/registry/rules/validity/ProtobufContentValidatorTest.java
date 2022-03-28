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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;

import java.util.Collections;

/**
 * Tests the Protobuf content validator.
 * @author eric.wittmann@gmail.com
 */
public class ProtobufContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidProtobufSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("protobuf-valid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidProtobufSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("protobuf-invalid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateProtobufWithImports() throws Exception {
        ContentHandle mode = resourceToContentHandle("mode.proto");
        ContentHandle tableInfo = resourceToContentHandle("table_info.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, tableInfo, Collections.singletonMap("mode.proto", mode));
    }

}
