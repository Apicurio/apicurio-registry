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

package io.apicurio.tests.utils;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import io.apicurio.tests.common.utils.BaseHttpUtils;
import io.apicurio.tests.common.utils.subUtils.RestConstants;
import io.restassured.response.Response;

/**
 * @author Fabian Martinez
 */
public class ArtifactUtils {


    public static Response getArtifact(String groupId, String artifactId) {
        return getArtifact(groupId, artifactId, "", 200);
    }

    public static Response getArtifact(String groupId, String artifactId, int returnCode) {
        return getArtifact(groupId, artifactId, "", returnCode);
    }

    public static Response getArtifact(String groupId, String artifactId, String version, int returnCode) {
        return
            BaseHttpUtils.getRequest(RestConstants.JSON, "/groups/" + encodeURIComponent(groupId) + "/artifacts/" + encodeURIComponent(artifactId) + "/" + version, returnCode);
    }

    public static Response createArtifact(String groupId, String artifactId, String artifact, int returnCode) {
        return  BaseHttpUtils.artifactPostRequest(artifactId, RestConstants.JSON, artifact, "/groups/" + encodeURIComponent(groupId) + "/artifacts", returnCode);
    }

    // ================================================================================

    private static String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

}
