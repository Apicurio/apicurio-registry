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
package io.apicurio.registry.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Fabian Martinez
 */
public class ArtifactIdValidator {

    public static final String GROUP_ID_ERROR_MESSAGE = "Character % and non ASCII characters are not allowed in group IDs.";
    public static final String ARTIFACT_ID_ERROR_MESSAGE = "Character % and non ASCII characters are not allowed in artifact IDs.";

    private ArtifactIdValidator() {
        //utility class
    }

    public static boolean isGroupIdAllowed(String groupId) {
        return isArtifactIdAllowed(groupId);
    }

    public static boolean isArtifactIdAllowed(String artifactId) {
        return Charset.forName(StandardCharsets.US_ASCII.name()).newEncoder().canEncode(artifactId)
                && !artifactId.contains("%");
    }

}
