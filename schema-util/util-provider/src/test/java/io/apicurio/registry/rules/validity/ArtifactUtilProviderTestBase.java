/*
 * Copyright 2021 Red Hat
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;

import io.apicurio.registry.content.ContentHandle;

/**
 * @author Fabian Martinez
 */
public class ArtifactUtilProviderTestBase {

    protected final String resourceToString(String resourceName) {
        try (InputStream stream = getClass().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    protected final ContentHandle resourceToContentHandle(String resourceName) {
        return ContentHandle.create(resourceToString(resourceName));
    }

}
