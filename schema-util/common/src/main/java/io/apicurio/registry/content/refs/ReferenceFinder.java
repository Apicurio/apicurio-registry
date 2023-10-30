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

package io.apicurio.registry.content.refs;

import java.util.Set;

import io.apicurio.registry.content.ContentHandle;

/**
 * @author eric.wittmann@gmail.com
 */
public interface ReferenceFinder {

    /**
     * Finds the set of external references in a piece of content.
     * @param content
     */
    public Set<ExternalReference> findExternalReferences(ContentHandle content);

}
