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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RegistryClientFactory.
 */
class RegistryClientFactoryTest {

    @Test
    void testCreateWithAnonymousAuth() {
        RegistryClient client = RegistryClientFactory.create(
                "http://localhost:8080"
        );

        assertThat(client).isNotNull();
    }

    @Test
    void testCreateWithConfigAnonymous() {
        ApicurioRegistryConfig config = new ApicurioRegistryConfig() {
            @Override
            public String url() {
                return "http://localhost:8080";
            }

            @Override
            public String defaultGroup() {
                return "default";
            }

            @Override
            public Optional<String> authToken() {
                return Optional.empty();
            }

            @Override
            public boolean cacheEnabled() {
                return true;
            }

            @Override
            public Optional<OAuth2Config> oauth2() {
                return Optional.empty();
            }
        };

        RegistryClient client = RegistryClientFactory.create(config);

        assertThat(client).isNotNull();
    }
}
