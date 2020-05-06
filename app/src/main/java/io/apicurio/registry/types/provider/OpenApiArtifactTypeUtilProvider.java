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

package io.apicurio.registry.types.provider;

import javax.enterprise.context.ApplicationScoped;

import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.OpenApiOrAsyncApiContentExtractor;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.OpenApiContentValidator;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class OpenApiArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {
    @Override
    public ArtifactType getArtifactType() {
        return ArtifactType.OPENAPI;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new JsonContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new OpenApiContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return OpenApiOrAsyncApiContentExtractor.INSTANCE;
    }
}
