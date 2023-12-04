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

package io.apicurio.registry.storage.error;

import lombok.Getter;


public class ContentNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -3640094007953927715L;

    @Getter
    private Long contentId;

    @Getter
    private String contentHash;


    public ContentNotFoundException(long contentId) {
        super(message(contentId, null));
        this.contentId = contentId;
    }


    public ContentNotFoundException(String contentHash) {
        super(message(null, contentHash));
        this.contentHash = contentHash;
    }


    private static String message(Long contentId, String contentHash) {
        if (contentId != null) {
            return "No content with ID '" + contentId + "' was found.";
        } else {
            return "No content with hash '" + contentHash + "' was found.";
        }
    }
}
