/*
 * Copyright 2019 Red Hat
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

import static io.apicurio.tests.utils.BaseHttpUtils.getRequest;
import static io.apicurio.tests.utils.BaseHttpUtils.putRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.tests.utils.BaseHttpUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

public class ArtifactUtils {

    public static Response getArtifact(String artifactId) {
        return getArtifact(artifactId, "", 200);
    }

    public static Response getArtifact(String artifactId, int returnCode) {
        return getArtifact(artifactId, "", returnCode);
    }

    public static Response getArtifact(String artifactId, String version, int returnCode) {
        return
            BaseHttpUtils.getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/" + version, returnCode);
    }

    public static Response getArtifactSpecificVersion(String artifactId, String version) {
        return getArtifactSpecificVersion(artifactId, version, 200);
    }

    public static Response getArtifactSpecificVersion(String artifactId, String version, int returnCode) {
        return getArtifact(artifactId, "versions/" + version, returnCode);
    }

    public static Response listArtifactVersions(String artifactId) {
        return listArtifactVersions(artifactId, 200);
    }

    public static Response listArtifactVersions(String artifactId, int returnCode) {
        return BaseHttpUtils.getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions", returnCode);
    }

    public static Response createArtifact(String artifact) {
        return createArtifact(artifact, 200);
    }

    public static Response createArtifact(String artifact, int returnCode) {
        return  BaseHttpUtils.postRequest(RestConstants.JSON, artifact, "/artifacts", returnCode);
    }

    public static ArtifactMetaData createArtifact(RegistryService apicurioService, ArtifactType atype, String artifactId, InputStream artifactData) {
        CompletionStage<ArtifactMetaData> csResult = apicurioService.createArtifact(atype, artifactId, null, artifactData);
        return ConcurrentUtil.result(csResult);
    }

    public static Response createArtifactNewVersion(String artifactId, String artifact, int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, artifact, "/artifacts/" + artifactId + "/versions", returnCode);
    }

    public static Response updateArtifact(String artifactId, String artifact) {
        return updateArtifact(artifactId, artifact, 200);
    }

    public static Response updateArtifact(String artifactId, String artifact, int returnCode) {
        return BaseHttpUtils.putRequest(RestConstants.JSON, artifact, "/artifacts/" + artifactId, returnCode);
    }

    public static ArtifactMetaData updateArtifact(RegistryService apicurioService, ArtifactType atype, String artifactId, InputStream artifactData) {
        CompletionStage<ArtifactMetaData> csResult = apicurioService.updateArtifact(artifactId, atype, artifactData);
        return ConcurrentUtil.result(csResult);
    }

    public static Response deleteArtifact(String artifactId) {
        return deleteArtifact(artifactId, 204);
    }

    public static Response deleteArtifact(String artifactId, int returnCode) {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId, returnCode);
    }

    public static Response deleteArtifactVersion(String artifactId, String version) {
        return deleteArtifactVersion(artifactId, version, 204);
    }

    public static Response deleteArtifactVersion(String artifactId, String version, int returnCode) {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions/" + version, returnCode);
    }

    public static Response createArtifactRule(String artifactId, String rule) {
        return createArtifactRule(artifactId, rule, 204);
    }

    public static Response createArtifactRule(String artifactId, String rule, int returnCode) {
        return BaseHttpUtils.rulesPostRequest(RestConstants.JSON, rule, "/artifacts/" + artifactId + "/rules", returnCode);
    }

    public static Response getArtifactRule(String artifactId, RuleType ruleType) {
        return getArtifactRule(artifactId, ruleType, 200);
    }

    public static Response getArtifactRule(String artifactId, RuleType ruleType, int returnCode) {
        return BaseHttpUtils.rulesGetRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules/" + ruleType, returnCode);
    }

    public static Response updateArtifactRule(String artifactId, RuleType ruleType, String rule) {
        return updateArtifactRule(artifactId, ruleType, rule, 200);
    }

    public static Response updateArtifactRule(String artifactId, RuleType ruleType, String rule, int returnCode) {
        return BaseHttpUtils.rulesPutRequest(RestConstants.JSON, rule, "/artifacts/" + artifactId + "/rules/" + ruleType, returnCode);
    }

    public static Response deleteArtifactString(String artifactId, RuleType ruleType) {
        return deleteArtifactRule(artifactId, ruleType, 200);
    }

    public static Response deleteArtifactRule(String artifactId, RuleType ruleType, int returnCode) {
        return BaseHttpUtils.rulesDeleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules/" + ruleType, returnCode);
    }

    public static Response listArtifactRules(String artifactId) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules", 200);
    }

    public static Response getArtifactMetadata(String artifactId) {
        return getArtifactMetadata(artifactId, 200);
    }

    public static Response getArtifactMetadata(String artifactId, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/meta", returnCode);
    }

    public static Response getArtifactVersionMetadata(String artifactId, String version) {
        return getArtifactVersionMetadata(artifactId, version, 200);
    }

    public static Response getArtifactVersionMetadata(String artifactId, String version, int returnCode) {
        return getRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions/" + version + "/meta", returnCode);
    }

    public static Response updateArtifactMetadata(String artifactId, String metadata) {
        return updateArtifactMetadata(artifactId, metadata, 204);
    }

    public static Response updateArtifactMetadata(String artifactId, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, "/artifacts/" + artifactId + "/meta", returnCode);
    }

    public static Response updateArtifactVersionMetadata(String artifactId, String version, String metadata) {
        return updateArtifactVersionMetadata(artifactId, version, metadata, 204);
    }

    public static Response updateArtifactVersionMetadata(String artifactId, String version, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, "/artifacts/" + artifactId + "/versions/" + version + "/meta", returnCode);
    }

    public static Response deleteArtifactVersionMetadata(String artifactId, String version) {
        return deleteArtifactVersionMetadata(artifactId, version, 204);
    }

    public static Response deleteArtifactVersionMetadata(String artifactId, String version, int returnCode) {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/versions/" + version + "/meta", returnCode);
    }

    public static HashMap<String, String> getFieldsFromResponse(JsonPath jsonPath) {
        return (HashMap<String, String>) jsonPath.getList("fields").get(0);
    }

    // ================================================================================

    public static Response getAllSchemas(int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, "", "/ccompat/subjects", returnCode);
    }

    public static Response getLatestVersionSchema(String nameOfSchema) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, "", "/ccompat/subjects/" + nameOfSchema + "/versions/latest", 200);
    }

    public static Response createSchema(String schemeDefinition, String schemaName, int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, schemeDefinition, "/ccompat/subjects/" + schemaName + "/versions", returnCode);
    }

    public static Response updateSchemaMetadata(String schemaName, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata, "/ccompat/subjects/" + schemaName + "/meta", returnCode);
    }
}
