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

package io.apicurio.registry.content.extract;

import static io.apicurio.registry.utils.StringUtil.isEmpty;

import io.apicurio.registry.content.ContentHandle;

/**
 * @author Ales Justin
 */
public interface ContentExtractor {
    /**
     * Extract metadata from content.
     * Return null if no content is extracted.
     *
     * @param content the content
     * @return extracted metadata or null if none
     */
    ExtractedMetaData extract(ContentHandle content);

    /**
     * Did we actually extracted something from the content.
     *
     * @param metaData the extracted metadata
     * @return true if extracted, false otherwise
     */
    default boolean isExtracted(ExtractedMetaData metaData) {
        if (metaData == null) {
            return false;
        }
        return !isEmpty(metaData.getName()) || !isEmpty(metaData.getDescription());
    }
}
