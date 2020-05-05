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

package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;

/**
 * Validates content.  Syntax and semantic validations are possible based on configuration.  An
 * implementation of this interface should exist for each content-type supported by the registry.
 *
 * @author eric.wittmann@gmail.com
 */
public interface ContentValidator {

    /**
     * Called to validate the given content.
     *
     * @param level           the level
     * @param artifactContent the content
     * @throws InvalidContentException for any invalid content
     */
    void validate(ValidityLevel level, ContentHandle artifactContent) throws InvalidContentException;

}
