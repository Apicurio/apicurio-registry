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

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class ContentAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 6415287691931770433L;

    @Getter
    private final Long contentId;


    public ContentAlreadyExistsException(long contentId) {
        super("Content with ID " + contentId + " already exists.");
        this.contentId = contentId;
    }
}