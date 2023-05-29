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
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the Avro content validator.
 * @author eric.wittmann@gmail.com
 */
public class AvroContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidAvroSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("avro-valid.json");
        AvroContentValidator validator = new AvroContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidAvroSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("avro-invalid.json");
        AvroContentValidator validator = new AvroContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateReferences() throws Exception {
        ContentHandle content = resourceToContentHandle("avro-valid-with-refs.json");
        AvroContentValidator validator = new AvroContentValidator();

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("com.example.search")
                    .artifactId("SearchResultType")
                    .version("1.0")
                    .name("com.example.search.SearchResultType").build());
            references.add(ArtifactReference.builder()
                    .groupId("com.example.actions")
                    .artifactId("UserAction")
                    .version("1.1")
                    .name("com.example.actions.UserAction").build());
            validator.validateReferences(content, references);
        }

        // Don't map either of the required references - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("com.example.search")
                    .artifactId("SearchResultType")
                    .version("1.0")
                    .name("com.example.search.SearchResultType").build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("com.example.search")
                    .artifactId("SearchResultType")
                    .version("1.0")
                    .name("com.example.search.SearchResultType").build());
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("WrongType")
                    .version("2.3")
                    .name("com.example.invalid.WrongType").build());
            validator.validateReferences(content, references);
        });
    }

}
