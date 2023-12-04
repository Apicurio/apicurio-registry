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


public class VersionAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 3567623491368394677L;

    @Getter
    private String groupId;

    @Getter
    private String artifactId;

    @Getter
    private String version;

    @Getter
    private Long globalId;


    public VersionAlreadyExistsException(String groupId, String artifactId, String version) {
        super(message(groupId, artifactId, version, null));
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }


    public VersionAlreadyExistsException(long globalId) {
        super(message(null, null, null, globalId));
        this.globalId = globalId;
    }


    private static String message(String groupId, String artifactId, String version, Long globalId) {
        if (globalId != null) {
            return "An artifact with global ID '" + globalId + "' already exists.";
        } else {
            return "An artifact version '" + version + "' for artifact ID '" + artifactId + "' " +
                    "in group '" + groupId + "' already exists.";
        }
    }
}
