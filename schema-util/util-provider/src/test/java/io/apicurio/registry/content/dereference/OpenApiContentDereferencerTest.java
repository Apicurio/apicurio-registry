/*
 * Copyright 2023 Red Hat Inc
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

package io.apicurio.registry.content.dereference;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;

/**
 * @author eric.wittmann@gmail.com
 */
public class OpenApiContentDereferencerTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testRewriteReferences() {
        ContentHandle content = resourceToContentHandle("openapi-to-rewrite.json");
        OpenApiDereferencer dereferencer = new OpenApiDereferencer();
        ContentHandle modifiedContent = dereferencer.rewriteReferences(content, Map.of(
                "./types/bar-types.json#/components/schemas/Bar", "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar",
                "./types/foo-types.json#/components/schemas/Foo", "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo"));
        
        ReferenceFinder finder = new OpenApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/bar-types.json#/components/schemas/Bar")));
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/foo-types.json#/components/schemas/Foo")));
    }

}
