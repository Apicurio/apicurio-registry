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
import io.apicurio.registry.types.ArtifactType;

/**
 * Interface providing different utils per artifact type
 * * compatibility checker
 * * content canonicalizer
 * * content validator
 * * rules
 * * etc ...
 *
 * @author Ales Justin
 */
public interface ArtifactTypeUtilProvider {
    ArtifactType getArtifactType();

    CompatibilityChecker getCompatibilityChecker();

    ContentCanonicalizer getContentCanonicalizer();

    ContentValidator getContentValidator();

    ContentExtractor getContentExtractor();
}
