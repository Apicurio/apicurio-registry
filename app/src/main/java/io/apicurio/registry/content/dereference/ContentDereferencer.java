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

package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

/**
 * Dereference some content!  This means replacing any reference inside the content by the full referenced content.
 * The result is an artifact content that can be used on its own.
 * @author carnalca@redhat.com
 */
public interface ContentDereferencer {

    /**
     * Called to dereference the given content to its dereferenced form
     * @param content
     */
    ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences);
}
