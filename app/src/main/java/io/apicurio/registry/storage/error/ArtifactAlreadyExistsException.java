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


public class ArtifactAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = -1015140450163088675L;

    @Getter
    private String groupId;

    @Getter
    private String artifactId;


    public ArtifactAlreadyExistsException(String groupId, String artifactId) {
        super(message(groupId, artifactId));
        this.artifactId = artifactId;
        this.groupId = groupId;
    }


    private static String message(String groupId, String artifactId) {
        return "An artifact with ID '" + artifactId + "' in group '" + groupId + "' already exists.";
    }
}
