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

package io.apicurio.tests.utils.subUtils;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.common.utils.BaseHttpUtils;
import io.apicurio.tests.common.utils.subUtils.RestConstants;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static io.apicurio.tests.common.utils.BaseHttpUtils.getRequest;
import static io.apicurio.tests.common.utils.BaseHttpUtils.putRequest;

public class ArtifactUtils {

    public static Response getArtifact(String artifactId) {
        return getArtifact(artifactId, "", 200);
    }

    public static Response getArtifact(String artifactId, int returnCode) {
        return getArtifact(artifactId, "", returnCode);
    }

    public static Response getArtifact(String artifactId, String version, int returnCode) {
        return
            BaseHttpUtils.getRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/" + version, returnCode);
    }

    public static Response getArtifactSpecificVersion(String artifactId, String version) {
        return getArtifactSpecificVersion(artifactId, version, 200);
    }

    public static Response getArtifactSpecificVersion(String artifactId, String version, int returnCode) {
        return getArtifact(artifactId, "versions/" + version, returnCode);
    }

    public static Response listArtifacts() {
        return BaseHttpUtils.getRequest(RestConstants.JSON, "/artifacts/", 200);
    }

    public static Response listArtifactVersions(String artifactId) {
        return listArtifactVersions(artifactId, 200);
    }

    public static Response listArtifactVersions(String artifactId, int returnCode) {
        return BaseHttpUtils.getRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/versions", returnCode);
    }

    public static Response createArtifact(String artifact) {
        return createArtifact(artifact, 200);
    }

    public static Response createArtifact(String artifact, int returnCode) {
        return  BaseHttpUtils.postRequest(RestConstants.JSON, artifact, "/artifacts", returnCode);
    }

    public static Response createArtifact(String artifactId, String artifact, int returnCode) {
        return  BaseHttpUtils.artifactPostRequest(artifactId, RestConstants.JSON, artifact, "/artifacts", returnCode);
    }

    public static ArtifactMetaData createArtifact(RegistryRestClient client, ArtifactType atype, String artifactId, InputStream artifactData) {
        return client.createArtifact(artifactId, atype, null, artifactData);
    }

    public static Response createArtifactNewVersion(String artifactId, String artifact, int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, artifact, "/artifacts/" + encodeURIComponent(artifactId) + "/versions", returnCode);
    }

    public static Response updateArtifact(String artifactId, String artifact) {
        return updateArtifact(artifactId, artifact, 200);
    }

    public static Response updateArtifact(String artifactId, String artifact, int returnCode) {
        return BaseHttpUtils.putRequest(RestConstants.JSON, artifact, "/artifacts/" + encodeURIComponent(artifactId), returnCode);
    }

    public static ArtifactMetaData updateArtifact(RegistryRestClient client, ArtifactType atype, String artifactId, InputStream artifactData) {
        return client.updateArtifact(artifactId, atype, artifactData);
    }

    public static Response deleteArtifact(String artifactId) throws Exception {
        return deleteArtifact(artifactId, 204);
    }

    public static Response deleteArtifact(String artifactId, int returnCode) throws Exception {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId), returnCode);
    }

    public static Response deleteArtifactVersion(String artifactId, String version) {
        return deleteArtifactVersion(artifactId, version, 204);
    }

    public static Response deleteArtifactVersion(String artifactId, String version, int returnCode) {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/versions/" + version, returnCode);
    }

    public static Response createArtifactRule(String artifactId, String rule) {
        return createArtifactRule(artifactId, rule, 204);
    }

    public static Response createArtifactRule(String artifactId, String rule, int returnCode) {
        return BaseHttpUtils.rulesPostRequest(RestConstants.JSON, rule, "/artifacts/" + encodeURIComponent(artifactId) + "/rules", returnCode);
    }

    public static Response getArtifactRule(String artifactId, RuleType ruleType) {
        return getArtifactRule(artifactId, ruleType, 200);
    }

    public static Response getArtifactRule(String artifactId, RuleType ruleType, int returnCode) {
        return BaseHttpUtils.rulesGetRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/rules/" + ruleType, returnCode);
    }

    public static Response updateArtifactRule(String artifactId, RuleType ruleType, String rule) {
        return updateArtifactRule(artifactId, ruleType, rule, 200);
    }

    public static Response updateArtifactRule(String artifactId, RuleType ruleType, String rule, int returnCode) {
        return BaseHttpUtils.rulesPutRequest(RestConstants.JSON, rule, "/artifacts/" + encodeURIComponent(artifactId) + "/rules/" + ruleType, returnCode);
    }

    public static Response deleteArtifactString(String artifactId, RuleType ruleType) {
        return deleteArtifactRule(artifactId, ruleType, 200);
    }

    public static Response deleteArtifactRule(String artifactId, RuleType ruleType, int returnCode) {
        return BaseHttpUtils.rulesDeleteRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/rules/" + ruleType, returnCode);
    }

    public static Response listArtifactRules(String artifactId) {
        return getRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/rules", 200);
    }

    public static Response getArtifactMetadata(String artifactId) {
        return getArtifactMetadata(artifactId, 200);
    }

    public static Response getArtifactMetadata(String artifactId, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/meta", returnCode);
    }

    public static Response getArtifactVersionMetadata(String artifactId, String version) {
        return getArtifactVersionMetadata(artifactId, version, 200);
    }

    public static Response getArtifactVersionMetadata(String artifactId, String version, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/versions/" + version + "/meta", returnCode);
    }

    public static Response updateArtifactMetadata(String artifactId, String metadata) {
        return updateArtifactMetadata(artifactId, metadata, 204);
    }

    public static Response updateArtifactMetadata(String artifactId, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, "/artifacts/" + encodeURIComponent(artifactId) + "/meta", returnCode);
    }

    public static Response updateArtifactVersionMetadata(String artifactId, String version, String metadata) {
        return updateArtifactVersionMetadata(artifactId, version, metadata, 204);
    }

    public static Response updateArtifactVersionMetadata(String artifactId, String version, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, "/artifacts/" + encodeURIComponent(artifactId) + "/versions/" + version + "/meta", returnCode);
    }

    public static Response deleteArtifactVersionMetadata(String artifactId, String version) {
        return deleteArtifactVersionMetadata(artifactId, version, 204);
    }

    public static Response deleteArtifactVersionMetadata(String artifactId, String version, int returnCode) {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/artifacts/" + encodeURIComponent(artifactId) + "/versions/" + version + "/meta", returnCode);
    }

    public static HashMap<String, String> getFieldsFromResponse(JsonPath jsonPath) {
        return (HashMap<String, String>) jsonPath.getList("fields").get(0);
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
