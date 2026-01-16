/*
 * Copyright 2024 Red Hat
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

package io.apicurio.registry.langchain4j;

import io.apicurio.registry.rest.client.RegistryClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ApicurioPromptTemplate.
 * <p>
 * Note: These tests focus on the constructor and basic behavior.
 * Full integration tests require a running registry server.
 */
@ExtendWith(MockitoExtension.class)
class ApicurioPromptTemplateTest {

    @Mock
    private RegistryClient client;

    @Test
    void testConstructorWithRequiredParameters() {
        ApicurioPromptTemplate template = new ApicurioPromptTemplate(
                client, "default", "test-prompt"
        );

        assertThat(template.getGroupId()).isEqualTo("default");
        assertThat(template.getArtifactId()).isEqualTo("test-prompt");
        assertThat(template.getVersion()).isNull();
    }

    @Test
    void testConstructorWithVersion() {
        ApicurioPromptTemplate template = new ApicurioPromptTemplate(
                client, "my-group", "my-prompt", "1.0"
        );

        assertThat(template.getGroupId()).isEqualTo("my-group");
        assertThat(template.getArtifactId()).isEqualTo("my-prompt");
        assertThat(template.getVersion()).isEqualTo("1.0");
    }

    @Test
    void testConstructorNullClientThrows() {
        assertThatThrownBy(() -> new ApicurioPromptTemplate(null, "group", "artifact"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client");
    }

    @Test
    void testConstructorNullGroupIdThrows() {
        assertThatThrownBy(() -> new ApicurioPromptTemplate(client, null, "artifact"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("groupId");
    }

    @Test
    void testConstructorNullArtifactIdThrows() {
        assertThatThrownBy(() -> new ApicurioPromptTemplate(client, "group", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("artifactId");
    }

    @Test
    void testApplyWithOddVarargsThrows() {
        ApicurioPromptTemplate template = new ApicurioPromptTemplate(
                client, "default", "greeting", null
        );

        assertThatThrownBy(() -> template.apply("name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key-value pairs");
    }

    @Test
    void testToString() {
        ApicurioPromptTemplate template = new ApicurioPromptTemplate(
                client, "my-group", "my-prompt", "1.0"
        );

        String result = template.toString();

        assertThat(result)
                .contains("my-group")
                .contains("my-prompt")
                .contains("1.0");
    }
}
