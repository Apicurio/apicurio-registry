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

import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;

/**
 * @author Ales Justin
 */
public abstract class AbstractArtifactTypeUtilProvider implements ArtifactTypeUtilProvider {

    private volatile CompatibilityChecker checker;
    private volatile ContentCanonicalizer canonicalizer;
    private volatile ContentValidator validator;
    private volatile ContentExtractor extractor;

    @Override
    public CompatibilityChecker getCompatibilityChecker() {
        if (checker == null) {
            checker = createCompatibilityChecker();
        }
        return checker;
    }

    protected abstract CompatibilityChecker createCompatibilityChecker();

    @Override
    public ContentCanonicalizer getContentCanonicalizer() {
        if (canonicalizer == null) {
            canonicalizer = createContentCanonicalizer();
        }
        return canonicalizer;
    }

    protected abstract ContentCanonicalizer createContentCanonicalizer();

    @Override
    public ContentValidator getContentValidator() {
        if (validator == null) {
            validator = createContentValidator();
        }
        return validator;
    }

    protected abstract ContentValidator createContentValidator();

    @Override
    public ContentExtractor getContentExtractor() {
        if (extractor == null) {
            extractor = createContentExtractor();
        }
        return extractor;
    }

    protected abstract ContentExtractor createContentExtractor();
}
