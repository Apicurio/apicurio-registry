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

package io.apicurio.registry.infinispan;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;

/**
 * Expose required cache serializable functions, consumers, etc methods.
 *
 * @author Ales Justin
 */
public interface StorageHandle {
    long nextContentId();
    ContentHandle canonicalizeContent(ArtifactType artifactType, ContentHandle content);
}
