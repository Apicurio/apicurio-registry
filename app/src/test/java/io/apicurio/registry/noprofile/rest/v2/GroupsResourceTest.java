/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.rest.v2;

import static io.restassured.RestAssured.given;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToObject;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.hamcrest.Matchers;
import org.jose4j.base64url.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.google.common.hash.Hashing;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.exception.RuleViolationException;
import io.apicurio.registry.rest.v2.beans.ArtifactOwner;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.Comment;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.NewComment;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class GroupsResourceTest extends AbstractResourceTestBase {

    private static final String GROUP = "GroupsResourceTest";

    @Test
    public void testDefaultGroup() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String jsonArtifactContent = resourceToString("jsonschema-valid.json");

        String nullGroup = "default";
        String group = "testDefaultGroup";

        // Create artifacts in null (default) group
        createArtifact(nullGroup, "testDefaultGroup/EmptyAPI/1", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(nullGroup, "testDefaultGroup/EmptyAPI/2", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(nullGroup, "testDefaultGroup/EmptyAPI/3", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(nullGroup, "testDefaultGroup/EmptyAPI/4", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(nullGroup, "testDefaultGroup/EmptyAPI/5", ArtifactType.OPENAPI, oaiArtifactContent);

        // Create 2 artifacts in other group
        createArtifact(group, "testDefaultGroup/EmptyAPI/1", ArtifactType.OPENAPI, jsonArtifactContent);
        createArtifact(group, "testDefaultGroup/EmptyAPI/2", ArtifactType.OPENAPI, jsonArtifactContent);

        // Search each group to ensure the correct # of artifacts.
        given()
                .when()
                .queryParam("group", nullGroup)
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(5));
        given()
                .when()
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(2));

        // Get the artifact content
        given()
                .when()
                .pathParam("groupId", nullGroup)
                .pathParam("artifactId", "testDefaultGroup/EmptyAPI/1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
        given()
                .when()
                .pathParam("groupId", group)
                .pathParam("artifactId", "testDefaultGroup/EmptyAPI/1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", not(equalTo("3.0.2")))
                .body("info.title", not(equalTo("Empty API")));
    }
    
    @Test
    public void testCreateArtifactRule() throws Exception
    {
    	String oaiArtifactContent = resourceToString("openapi-empty.json");
    	createArtifact("testCreateArtifactRule", "testCreateArtifactRule/EmptyAPI/1", ArtifactType.OPENAPI, oaiArtifactContent);
    	
    	//Test Rule type null
    	Rule nullType = new Rule();
    	nullType.setType(null);
    	nullType.setConfig("TestConfig");
    	given()
	        	.when()
	        	.contentType(CT_JSON)
	    		.pathParam("groupId", "testCreateArtifactRule")
	    		.pathParam("artifactId", "testCreateArtifactRule/EmptyAPI/1")
	    		.body(nullType)
	    		.post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
	        	.then()	
	    		.statusCode(400);
    	
    	//Test Rule config null
    	Rule nullConfig = new Rule();
    	nullConfig.setType(RuleType.VALIDITY);
    	nullConfig.setConfig(null);
    	given()
	        	.when()
	        	.contentType(CT_JSON)
	    		.pathParam("groupId", "testCreateArtifactRule")
	    		.pathParam("artifactId", "testCreateArtifactRule/EmptyAPI/1")
	    		.body(nullConfig)
	    		.post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
	        	.then()
	    		.statusCode(400);
    	
    	//Test Rule config empty
    	Rule emptyConfig = new Rule();
    	emptyConfig.setType(RuleType.VALIDITY);
    	emptyConfig.setConfig("");
    	given()
	        	.when()
	        	.contentType(CT_JSON)
	    		.pathParam("groupId", "testCreateArtifactRule")
	    		.pathParam("artifactId", "testCreateArtifactRule/EmptyAPI/1")
	    		.body(emptyConfig)
	    		.post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
	        	.then()
	    		.statusCode(400);
    	
    }

    @Test
    public void testUpdateArtifactOwner() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        createArtifact("testUpdateArtifactOwner", "testUpdateArtifactOwner/EmptyAPI/1",ArtifactType.OPENAPI, oaiArtifactContent);

        ArtifactOwner artifactOwner = new ArtifactOwner("newOwner");

        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", "testUpdateArtifactOwner")
                .pathParam("artifactId", "testUpdateArtifactOwner/EmptyAPI/1")
                .body(artifactOwner)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/owner")
                .then()
                .statusCode(204);
    }

    @Test
    public void testUpdateEmptyArtifactOwner() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        createArtifact("testUpdateEmptyArtifactOwner", "testUpdateEmptyArtifactOwner/EmptyAPI/1",ArtifactType.OPENAPI, oaiArtifactContent);

        ArtifactOwner artifactOwner = new ArtifactOwner("");

        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", "testUpdateEmptyArtifactOwner")
                .pathParam("artifactId", "testUpdateEmptyArtifactOwner/EmptyAPI/1")
                .body(artifactOwner)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/owner")
                .then()
                .statusCode(400);
    }

    @Test
    public void testMultipleGroups() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String jsonArtifactContent = resourceToString("jsonschema-valid.json");

        String group1 = "testMultipleGroups_1";
        String group2 = "testMultipleGroups_2";

        // Create 5 artifacts in Group 1
        createArtifact(group1, "testMultipleGroups/EmptyAPI/1", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/2", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/3", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/4", ArtifactType.OPENAPI, oaiArtifactContent);
        createArtifact(group1, "testMultipleGroups/EmptyAPI/5", ArtifactType.OPENAPI, oaiArtifactContent);

        // Create 2 artifacts in Group 2
        createArtifact(group2, "testMultipleGroups/EmptyAPI/1", ArtifactType.OPENAPI, jsonArtifactContent);
        createArtifact(group2, "testMultipleGroups/EmptyAPI/2", ArtifactType.OPENAPI, jsonArtifactContent);

        // Get group 1 metadata
        given()
                .when()
                .pathParam("groupId", group1)
                .get("/registry/v2/groups/{groupId}")
                .then()
                .statusCode(200)
                .body("id", equalTo("testMultipleGroups_1"));

        // Get group 2 metadata
        given()
                .when()
                .pathParam("groupId", group2)
                .get("/registry/v2/groups/{groupId}")
                .then()
                .statusCode(200)
                .body("id", equalTo("testMultipleGroups_2"));

        // Search each group to ensure the correct # of artifacts.
        given()
                .when()
                .queryParam("group", group1)
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(5));
        given()
                .when()
                .queryParam("group", group2)
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(2));

        // Get the artifact content
        given()
                .when()
                .pathParam("groupId", group1)
                .pathParam("artifactId", "testMultipleGroups/EmptyAPI/1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
        given()
                .when()
                .pathParam("groupId", group2)
                .pathParam("artifactId", "testMultipleGroups/EmptyAPI/1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", not(equalTo("3.0.2")))
                .body("info.title", not(equalTo("Empty API")));

        //Test delete group operations
        // Delete group 1 metadata
        given()
                .when()
                .pathParam("groupId", group1)
                .delete("/registry/v2/groups/{groupId}")
                .then()
                .statusCode(204);

        // Delete group 2 metadata
        given()
                .when()
                .pathParam("groupId", group2)
                .delete("/registry/v2/groups/{groupId}")
                .then()
                .statusCode(204);

        // Get group 1 metadata again, should return 404
        given()
                .when()
                .pathParam("groupId", group1)
                .get("/registry/v2/groups/{groupId}")
                .then()
                .statusCode(404);

        // Get group 1 metadata again, should return 404
        given()
                .when()
                .pathParam("groupId", group2)
                .get("/registry/v2/groups/{groupId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testCreateArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact - indicate the type via a header param
        createArtifact(GROUP, "testCreateArtifact/EmptyAPI/1", ArtifactType.OPENAPI, artifactContent);

        // Create OpenAPI artifact - indicate the type via the content-type
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/2")
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(GROUP))
                .body("version", equalTo("1"))
                .body("id", equalTo("testCreateArtifact/EmptyAPI/2"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Try to create a duplicate artifact ID (should fail)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/1")
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("An artifact with ID 'testCreateArtifact/EmptyAPI/1' in group 'GroupsResourceTest' already exists."));

        // Try to create an artifact with an invalid artifact type
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=INVALID_ARTIFACT_TYPE")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/InvalidAPI")
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(400);

        // Create OpenAPI artifact - don't provide the artifact type
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI/detect")
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("id", equalTo("testCreateArtifact/EmptyAPI/detect"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Create artifact with empty content (should fail)
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyContent")
                .body("")
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(400);

        // Create OpenAPI artifact - provide a custom version #
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI-customVersion")
                .header("X-Registry-Version", "1.0.2")
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(GROUP))
                .body("version", equalTo("1.0.2"))
                .body("id", equalTo("testCreateArtifact/EmptyAPI-customVersion"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Create OpenAPI artifact - provide a custom name
        String customName = "CUSTOM NAME";
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI-customName")
                .header("X-Registry-Name", customName)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(GROUP))
                .body("name", equalTo(customName))
                .body("id", equalTo("testCreateArtifact/EmptyAPI-customName"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Create OpenAPI artifact - provide a custom description
        String customDescription = "CUSTOM DESCRIPTION";
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI-customDescription")
                .header("X-Registry-Description", customDescription)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(GROUP))
                .body("description", equalTo(customDescription))
                .body("id", equalTo("testCreateArtifact/EmptyAPI-customDescription"))
                .body("type", equalTo(ArtifactType.OPENAPI));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = CURRENT_ENV, matches = CURRENT_ENV_MAS_REGEX)
    @DisabledOnOs(OS.WINDOWS)
    public void testCreateArtifactNoAscii() {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact - provide a custom No-ASCII name
        String customNoASCIIName = "CUSTOM NAME with NO-ASCII char č";
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI-customNameEncoded")
                .header("X-Registry-Name-Encoded", Base64.encode(customNoASCIIName.getBytes(StandardCharsets.UTF_8)))
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(GROUP))
                .body("name", equalTo(customNoASCIIName))
                .body("id", equalTo("testCreateArtifact/EmptyAPI-customNameEncoded"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Create OpenAPI artifact - provide a custom No-ASCII description
        String customNoASCIIDescription = "CUSTOM DESCRIPTION with NO-ASCII char č";
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI-customDescriptionEncoded")
                .header("X-Registry-Description-Encoded", Base64.encode(customNoASCIIDescription.getBytes(StandardCharsets.UTF_8)))
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(GROUP))
                .body("description", equalTo(customNoASCIIDescription))
                .body("id", equalTo("testCreateArtifact/EmptyAPI-customDescriptionEncoded"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Create OpenAPI artifact - provide a custom name and encoded custom name (conflict - should fail)
        String customName = "CUSTOM NAME";
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifact/EmptyAPI-customNameConflict")
                .header("X-Registry-Name", customName)
                .header("X-Registry-Name-Encoded", Base64.encode(customNoASCIIName.getBytes(StandardCharsets.UTF_8)))
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(409);
    }

    @Test
    public void testGetArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testGetArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Get the artifact content
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifact/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Try to get artifact content for an artifact that doesn't exist.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifact/MissingAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(404)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testGetArtifact/MissingAPI' in group 'GroupsResourceTest' was found."));
    }

    @Test
    public void testUpdateArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testUpdateArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update OpenAPI artifact
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Get the artifact content (should be the updated content)
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Try to update an artifact that doesn't exist.
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testUpdateArtifact/MissingAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(404);

        // Try to update an artifact with empty content
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body("")
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(400);

        // Update OpenAPI artifact with a custom version
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Version", "3.0.0.Final")
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("version", equalTo("3.0.0.Final"))
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Update OpenAPI artifact with a custom name
        String customName = "CUSTOM NAME";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Name", customName)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("name", equalTo(customName))
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Update OpenAPI artifact with a custom description
        String customDescription = "CUSTOM DESCRIPTION";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Description", customDescription)
                .pathParam("artifactId", "testUpdateArtifact/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("description", equalTo(customDescription))
                .body("id", equalTo("testUpdateArtifact/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI));

    }

    @Test
    @DisabledIfEnvironmentVariable(named = CURRENT_ENV, matches = CURRENT_ENV_MAS_REGEX)
    @DisabledOnOs(OS.WINDOWS)
    public void testUpdateArtifactNoAscii() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testUpdateArtifactNoAscii/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update OpenAPI artifact with a custom no-ascii name
        String customNoASCIIName = "CUSTOM NAME with NO-ASCII char ě";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Name-Encoded", Base64.encode(customNoASCIIName.getBytes(StandardCharsets.UTF_8)))
                .pathParam("artifactId", "testUpdateArtifactNoAscii/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("name", equalTo(customNoASCIIName))
                .body("id", equalTo("testUpdateArtifactNoAscii/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Update OpenAPI artifact with a custom no-ascii description
        String customNoASCIIDescription = "CUSTOM DESCRIPTION with NO-ASCII char ě";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Description-Encoded", Base64.encode(customNoASCIIDescription.getBytes(StandardCharsets.UTF_8)))
                .pathParam("artifactId", "testUpdateArtifactNoAscii/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("description", equalTo(customNoASCIIDescription))
                .body("id", equalTo("testUpdateArtifactNoAscii/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Try to Update artifact with a custom name and encoded name (conflict - should fail)
        String customName = "CUSTOM NAME";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Name", customName)
                .header("X-Registry-Name-Encoded", Base64.encode(customNoASCIIName.getBytes(StandardCharsets.UTF_8)))
                .pathParam("artifactId", "testUpdateArtifactNoAscii/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(409);
    }

    @Test
    public void testDeleteArtifact() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testDeleteArtifact/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Make sure we can get the artifact content
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Delete the artifact
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(204);

        // Try to get artifact content for an artifact that doesn't exist.
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testDeleteArtifact/EmptyAPI")
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                    .then()
                    .statusCode(404)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No artifact with ID 'testDeleteArtifact/EmptyAPI' in group 'GroupsResourceTest' was found."));
        });

        // Try to delete an artifact that doesn't exist.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifact/MissingAPI")
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testDeleteArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testDeleteArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Make sure we can get the artifact content
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Create a new version of the artifact
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("version", equalTo("2"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        //Get the artifact version 1
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", "1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Delete the artifact version 1
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", "1")
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(204);

        // Try to get artifact version 1 that doesn't exist.
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                    .pathParam("version", "1")
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                    .then()
                    .statusCode(404)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No version '1' found for artifact with ID 'testDeleteArtifactVersion/EmptyAPI' in group 'GroupsResourceTest'."));
        });

        //Get the artifact version 2
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", "2")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Delete the artifact version 2
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", "2")
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(204);

        // Try to get artifact version 2 that doesn't exist.
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                    .pathParam("version", "2")
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                    .then()
                    .statusCode(404)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No version '2' found for artifact with ID 'testDeleteArtifactVersion/EmptyAPI' in group 'GroupsResourceTest'."));
        });

        // Try to delete an artifact version 2 that doesn't exist.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", "2")
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testDeleteArtifactsInGroup() throws Exception {
        String group = "testDeleteArtifactsInGroup";
        String artifactContent = resourceToString("openapi-empty.json");

        // Create several artifacts in the group.
        createArtifact(group, "EmptyAPI-1", ArtifactType.OPENAPI, artifactContent);
        createArtifact(group, "EmptyAPI-2", ArtifactType.OPENAPI, artifactContent);
        createArtifact(group, "EmptyAPI-3", ArtifactType.OPENAPI, artifactContent);

        // Make sure we can search for all three artifacts in the group.
        given()
                .when()
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(3));

        // Delete the artifacts in the group
        given()
                .when()
                .pathParam("groupId", group)
                .delete("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(204);

        // Verify that all 3 artifacts were deleted
        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("group", group)
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(200)
                    .body("count", equalTo(0));
        });
    }

    @Test
    public void testListArtifactsInGroup() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "testListArtifactsInGroup";

        // Create several artifacts in a group.
        createArtifact(group, "EmptyAPI-1", ArtifactType.OPENAPI, artifactContent);
        createArtifact(group, "EmptyAPI-2", ArtifactType.OPENAPI, artifactContent);
        createArtifact(group, "EmptyAPI-3", ArtifactType.OPENAPI, artifactContent);

        // List the artifacts in the group
        given()
                .when()
                .pathParam("groupId", group)
                .get("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(3));

        // Add two more artifacts to the group.
        createArtifact(group, "EmptyAPI-4", ArtifactType.OPENAPI, artifactContent);
        createArtifact(group, "EmptyAPI-5", ArtifactType.OPENAPI, artifactContent);

        // List the artifacts in the group again
        given()
                .when()
                .pathParam("groupId", group)
                .get("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(5));

        // Try to list artifacts for a group that doesn't exist

        // List the artifacts in the group
        given()
                .when()
                .pathParam("groupId", group + "-doesnotexist")
                .get("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(0));

    }

    @Test
    public void testListArtifactVersions() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testListArtifactVersions/EmptyAPI";

        // Create an artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        for (int idx = 0; idx < 5; idx++) {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                    .then()
                    .statusCode(200)
                    .body("id", equalTo(artifactId))
                    .body("type", equalTo(ArtifactType.OPENAPI));
        }

        // List the artifact versions
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
//                .log().all()
                .statusCode(200)
                .body("count", equalTo(6))
                .body("versions[0].version", notNullValue());

        // Try to list artifact versions for an artifact that doesn't exist.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testListArtifactVersions/MissingAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(404);

    }

    @Test
    public void testCreateArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testCreateArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Create a new version of the artifact
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("version", equalTo("2"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Get the artifact content (should be the updated content)
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API (Updated)"));

        // Try to create a new version of an artifact that doesn't exist.
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("artifactId", "testCreateArtifactVersion/MissingAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(404);

        // Try to create a new version of the artifact with empty content
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body("")
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(400);

        // Create another new version of the artifact with a custom version #
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-Version", "3.0.0.Final")
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("version", equalTo("3.0.0.Final"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Create another new version of the artifact with a custom name
        String customName = "CUSTOM NAME";

        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-Name", customName)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("name", equalTo(customName));

        // Create another new version of the artifact with a custom description
        String customDescription = "CUSTOM DESCRIPTION";

        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-Description", customDescription)
                .pathParam("artifactId", "testCreateArtifactVersion/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("description", equalTo(customDescription));

    }

    @Test
    @DisabledIfEnvironmentVariable(named = CURRENT_ENV, matches = CURRENT_ENV_MAS_REGEX)
    @DisabledOnOs(OS.WINDOWS)
    public void testCreateArtifactVersionNoAscii() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testCreateArtifactVersionNoAscii/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Create another new version of the artifact with a custom No-ASCII name and description
        String customNameNoASCII = "CUSTOM NAME WITH NO-ASCII CHAR ě";
        String customDescriptionNoASCII = "CUSTOM DESCRIPTION WITH NO-ASCII CHAR ě";

        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-Name-Encoded", Base64.encode(customNameNoASCII.getBytes(StandardCharsets.UTF_8)))
                .header("X-Registry-Description-Encoded", Base64.encode(customDescriptionNoASCII.getBytes(StandardCharsets.UTF_8)))
                .pathParam("artifactId", "testCreateArtifactVersionNoAscii/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("name", equalTo(customNameNoASCII))
                .body("description", equalTo(customDescriptionNoASCII));

        // Get artifact metadata (should has the custom name and description)
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactVersionNoAscii/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(200)
                .body("name", equalTo(customNameNoASCII))
                .body("description", equalTo(customDescriptionNoASCII));

        // Try to create new version of the artifact with a custom name and encoded name (conflict)
        String customName = "CUSTOM NAME";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-Name-Encoded", Base64.encode(customNameNoASCII.getBytes(StandardCharsets.UTF_8)))
                .header("X-Registry-Name", customName)
                .pathParam("artifactId", "testCreateArtifactVersionNoAscii/EmptyAPI")
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(409);
    }

    @Test
    public void testCreateArtifactVersionValidityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("jsonschema-invalid.json");
        String artifactId = "testCreateArtifact/ValidityRuleViolation";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Create a new version of the artifact with invalid syntax
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(artifactContentInvalidSyntax)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", startsWith("Syntax or semantic violation for JSON Schema artifact."));
    }

    @Test
    public void testCreateArtifactVersionCompatibilityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("jsonschema-valid-incompatible.json");
        String artifactId = "testCreateArtifact/ValidJson";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(rule)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        });

        // Create a new version of the artifact with invalid syntax
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(artifactContentInvalidSyntax)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", startsWith("Incompatible artifact: testCreateArtifact/ValidJson [JSON], num" +
                        " of incompatible diffs: {1}, list of diff types: [SUBSCHEMA_TYPE_CHANGED at /properties/age]"))
                .body("causes[0].description", equalTo(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription()))
                .body("causes[0].context", equalTo("/properties/age"));

    }

    @Test
    public void testGetArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create an artifact
        createArtifact(GROUP, "testGetArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        List<String> versions = new ArrayList<>();
        for (int idx = 0; idx < 5; idx++) {
            String version = given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", GROUP)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                    .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactVersion/EmptyAPI"))
                    .body("type", equalTo(ArtifactType.OPENAPI))
                    .extract().body().path("version");
            versions.add(version);
        }

        // Now get each version of the artifact
        for (int idx = 0; idx < 5; idx++) {
            String version = versions.get(idx);
            String expected = "Empty API (Update " + idx + ")";
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                    .pathParam("version", version)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                    .then()
                    .statusCode(200)
                    .body("info.title", equalTo(expected));
        }

        // Now get a version that doesn't exist.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactVersion/EmptyAPI")
                .pathParam("version", 12345)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(404);

        // Now get a version of an artifact that doesn't exist.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactVersion/MissingAPI")
                .pathParam("version", "1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(404);
    }

    @Test
    public void testGetArtifactMetaDataByContent() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create an artifact
        createArtifact(GROUP, "testGetArtifactMetaDataByContent/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Update the artifact 5 times
        for (int idx = 0; idx < 5; idx++) {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", GROUP)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                    .body(artifactContent.replace("Empty API", "Empty API (Update " + idx + ")"))
                    .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                    .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactMetaDataByContent/EmptyAPI"))
                    .body("groupId", equalTo(GROUP))
                    .body("type", equalTo(ArtifactType.OPENAPI))
                    .extract().body().path("version");
        }

        // Get meta-data by content
        String searchContent = artifactContent.replace("Empty API", "Empty API (Update 2)");
        Integer globalId1 = given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .body(searchContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .extract().body().path("globalId");

        // Now add some extra whitespace/formatting to the content and try again
        searchContent = searchContent.replace("{", "{\n").replace("}", "\n}");
        Integer globalId2 = given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .queryParam("canonical", "true")
                .body(searchContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .extract().body().path("globalId");

        // Should return the same meta-data
        assertEquals(globalId1, globalId2);

        // Try the same (extra whitespace) content but without the "canonical=true" param (should fail with 404)
        searchContent = searchContent.replace("{", "{\n").replace("}", "\n}");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .body(searchContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(404);

        // Get meta-data by empty content (400 error)
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaDataByContent/EmptyAPI")
                .body("")
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(400);

    }

    @Test
    public void testArtifactRules() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testArtifactRules/EmptyAPI";

        // Create an artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Try to add the rule again - should get a 409
        final Rule finalRule = rule;
        TestUtils.retry(() -> {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .body(finalRule)
                    .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                    .then()
                    .statusCode(409)
                    .body("error_code", equalTo(409))
                    .body("message", equalTo("A rule named 'VALIDITY' already exists."));
        });

        // Add another rule
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(rule)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        });

        // Get the list of rules (should be 2 of them)
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[2]", nullValue());

        // Update a rule's config
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(rule)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("FULL"));

        // Get a single (updated) rule by name
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("FULL"));
        });

        // Delete a rule
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY")
                .then()
                .statusCode(204)
                .body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/COMPATIBILITY")
                    .then()
                    .statusCode(404)
                    .contentType(ContentType.JSON)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No rule named 'COMPATIBILITY' was found."));
        });

        // Get the list of rules (should be 1 of them)
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
//                .log().all()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                .body("[1]", nullValue());

        // Delete all rules
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204);

        // Get the list of rules (no rules now)
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", nullValue());
        });

        // Add a rule to an artifact that doesn't exist.
        rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "MissingArtifact")
                .body(rule)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(404)
                .body(anything());
    }

    @Test
    public void testArtifactMetaData() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testGetArtifactMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Get the artifact meta-data
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("version", anything())
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."))
                .extract()
                .as(ArtifactMetaData.class);

        // Try to get artifact meta-data for an artifact that doesn't exist.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/MissingAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(404)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No artifact with ID 'testGetArtifactMetaData/MissingAPI' in group 'GroupsResourceTest' was found."));

        // Update the artifact meta-data
        String metaData = "{\"name\": \"Empty API Name\", \"description\": \"Empty API description.\", \"labels\":[\"Empty API label 1\",\"Empty API label 2\"], \"properties\":{\"additionalProp1\": \"Empty API additional property\"}}";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .body(metaData)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(204);


        // Get the (updated) artifact meta-data
        TestUtils.retry(() -> {
            List<String> expectedLabels = Arrays.asList("Empty API label 1", "Empty API label 2");
            Map<String, String> expectedProperties = new HashMap<>();
            expectedProperties.put("additionalProp1", "Empty API additional property");

            String version = given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                    .then()
                    .statusCode(200)
                    .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                    .body("version", anything())
                    .body("name", equalTo("Empty API Name"))
                    .body("description", equalTo("Empty API description."))
                    .body("labels", equalToObject(expectedLabels))
                    .body("properties", equalToObject(expectedProperties))
                    .extract().body().path("version");

            // Make sure the version specific meta-data also returns all the custom meta-data
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                    .pathParam("version", version)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
                    .then()
                    .statusCode(200)
                    .body("name", equalTo("Empty API Name"))
                    .body("description", equalTo("Empty API description."))
                    .body("labels", equalToObject(expectedLabels))
                    .body("properties", equalToObject(expectedProperties))
                    .extract().body().path("version");
        });

        // Update the artifact content and then make sure the name/description meta-data is still available
        String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .body(updatedArtifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // Verify the artifact meta-data name and description are still set.
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(200)
                .body("id", equalTo("testGetArtifactMetaData/EmptyAPI"))
                .body("version", anything())
                .body("name", equalTo("Empty API (Updated)"))
                .body("description", equalTo("An example API design using OpenAPI."));
    }

    @Test
    public void testPropertyValueNotNull() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        int idx = 0;
        String title = "Empty API " + idx;
        String artifactId = "Empty-" + idx;
        this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));

        Map<String, String> props = new HashMap<>();
        props.put("test-key", null);

        // Update the artifact meta-data
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(title);
        metaData.setDescription("Some description of an API");
        metaData.setProperties(props);
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .body(metaData)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(400);

    }

    @Test
    public void testArtifactVersionMetaData() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String updatedArtifactContent_v2 = artifactContent.replace("Empty API", "Empty API (v2)");
        String updatedArtifactContent_v3 = artifactContent.replace("Empty API", "Empty API (v3)");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testArtifactVersionMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Create a new version of the artifact
        String version2 = given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .body(updatedArtifactContent_v2)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("type", equalTo(ArtifactType.OPENAPI))
                .extract().body().path("version");

        // Create another new version of the artifact
        String version3 = given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .body(updatedArtifactContent_v3)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("type", equalTo(ArtifactType.OPENAPI))
                .extract().body().path("version");

        // Get meta-data for v2
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                .statusCode(200)
                .body("version", equalTo(version2))
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API (v2)"))
                .body("description", equalTo("An example API design using OpenAPI."))
                .extract()
                .as(VersionMetaData.class);

        // Update the version meta-data
        String metaData = "{\"name\": \"Updated Name\", \"description\": \"Updated description.\"}";
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version2)
                .body(metaData)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                    .pathParam("version", version2)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
                    .then()
                    .statusCode(200)
                    .body("version", equalTo(version2))
                    .body("type", equalTo(ArtifactType.OPENAPI))
                    .body("createdOn", anything())
                    .body("name", equalTo("Updated Name"))
                    .body("description", equalTo("Updated description."));
        });

        // Get the version meta-data for the version we **didn't** update
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", version3)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                .statusCode(200)
                .body("version", equalTo(version3))
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API (v3)"))
                .body("description", equalTo("An example API design using OpenAPI."));

        // Get the version meta-data for a non-existant version
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testArtifactVersionMetaData/EmptyAPI")
                .pathParam("version", 12345)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                .statusCode(404);

    }

    @Test
    public void testYamlContentType() throws Exception {
        String artifactId = "testYamlContentType";
        String artifactType = ArtifactType.OPENAPI;
        String artifactContent = resourceToString("openapi-empty.yaml");

        // Create OpenAPI artifact (from YAML)
        given()
                .config(RestAssuredConfig.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs(CT_YAML, ContentType.TEXT)))
                .when()
                .contentType(CT_YAML)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .pathParam("groupId", GROUP)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."))
                .body("type", equalTo(artifactType));

        // Get the artifact content (should be JSON)
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testYamlContentType")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .header("Content-Type", Matchers.containsString(CT_JSON))
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));
    }


    @Test
    public void testWsdlArtifact() throws Exception {
        String artifactId = "testWsdlArtifact";
        String artifactType = ArtifactType.WSDL;
        String artifactContent = resourceToString("sample.wsdl");

        // Create OpenAPI artifact (from YAML)
        given()
                .config(RestAssuredConfig.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs(CT_XML, ContentType.TEXT)))
                .when()
                .contentType(CT_XML)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType)
                .pathParam("groupId", GROUP)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType));

        // Get the artifact content (should be XML)
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testWsdlArtifact")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .header("Content-Type", Matchers.containsString(CT_XML));
    }

    @Test
    public void testCreateAlreadyExistingArtifact() throws Exception {
        final String artifactId = UUID.randomUUID().toString();
        final String artifactContent = resourceToString("openapi-empty.json");
        final String updatedArtifactContent = artifactContent.replace("Empty API", "Empty API (Updated)");
        final String v3ArtifactContent = artifactContent.replace("Empty API", "Empty API (Version 3)");
        final String artifactName = "ArtifactNameFromHeader";
        final String artifactDescription = "ArtifactDescriptionFromHeader";

        // Create OpenAPI artifact - indicate the type via a header param
        Integer globalId1 = createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Try to create the same artifact ID (should fail)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .pathParam("groupId", GROUP)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("An artifact with ID '" + artifactId + "' in group 'GroupsResourceTest' already exists."));

        // Try to create the same artifact ID with Return for if exists (should return same artifact)
        given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .pathParam("groupId", GROUP)
                .queryParam("ifExists", IfExists.RETURN)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("version", equalTo("1"))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."));

        // Try to create the same artifact ID with Update for if exists (should update the artifact)
        ValidatableResponse resp = given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .pathParam("groupId", GROUP)
                .queryParam("ifExists", IfExists.UPDATE)
                .body(updatedArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("version", equalTo("2"))
                .body("description", equalTo("An example API design using OpenAPI."));
        /*Integer globalId2 = */resp.extract().body().path("globalId");

        // Try to create the same artifact ID with ReturnOrUpdate - should return v1 (matching content)
        resp = given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .pathParam("groupId", GROUP)
                .queryParam("ifExists", IfExists.RETURN_OR_UPDATE)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI));

        Integer globalId3 = resp.extract().body().path("globalId");

        assertEquals(globalId1, globalId3);

        // Try to create the same artifact ID with ReturnOrUpdate and updated content - should create a new version
        // and use name and description from headers
        resp = given()
                .when()
                .contentType(CT_JSON + "; artifactType=OPENAPI")
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-Name", artifactName)
                .header("X-Registry-Description", artifactDescription)
                .pathParam("groupId", GROUP)
                .queryParam("ifExists", IfExists.RETURN_OR_UPDATE)
                .body(v3ArtifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("version", equalTo("3"))
                .body("name", equalTo(artifactName))
                .body("description", equalTo(artifactDescription))
                .body("type", equalTo(ArtifactType.OPENAPI));
    }

    @Test
    public void testDeleteArtifactWithRule() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testDeleteArtifactWithRule/EmptyAPI";

        // Create an artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(rule)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Get a single rule by name
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Delete the artifact
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(204);

        // Get a single rule by name (should be 404 because the artifact is gone)
        // Also try to get the artifact itself (should be 404)
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY")
                    .then()
                    .statusCode(404);
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                    .then()
                    .statusCode(404);
        });

        // Re-create the artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Get a single rule by name (should be 404 because the artifact is gone)
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/VALIDITY")
                .then()
                .statusCode(404);

        // Add the same rule - should work because the old rule was deleted when the artifact was deleted.
        rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(rule)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());
    }

    @Test
    public void testCorrectGroup() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String jsonArtifactContent = resourceToString("jsonschema-valid.json");

        String groupId = "test-correct-group";

        String artifactId = "test-artifact-a";

        // Create 1 artifact through the new api
        createArtifact(groupId, artifactId, ArtifactType.OPENAPI, oaiArtifactContent);

        // Create 1 artifact through the old api
        createArtifact(artifactId, ArtifactType.OPENAPI, jsonArtifactContent);

        // Search each group to ensure the correct # of artifacts.
        given()
                .when()
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(2));
        given()
                .when()
                .queryParam("group", groupId)
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", equalTo(1));

        // Get the artifact content
        given()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Verify the metadata
        given()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(groupId))
                .body("id", equalTo(artifactId))
                .body("version", anything())
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("createdOn", anything())
                .body("name", equalTo("Empty API"))
                .body("description", equalTo("An example API design using OpenAPI."));
    }

    @Test
    public void testCustomArtifactVersion() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String groupId = "testCustomArtifactVersion";
        String artifactId = "MyVersionedAPI";

        // Create OpenAPI artifact version 1.0.0
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", groupId)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .header("X-Registry-Version", "1.0.0")
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("groupId", equalTo(groupId))
                .body("version", equalTo("1.0.0"));

        // Make sure we can get the artifact content by version
        given()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .pathParam("version", "1.0.0")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Make sure we can get the artifact meta-data by version
        given()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .pathParam("version", "1.0.0")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
                .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("groupId", equalTo(groupId))
                .body("version", equalTo("1.0.0"));

        // Add version 1.0.1
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-Version", "1.0.1")
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .body(artifactContent.replace("Empty API", "Empty API (Version 1.0.1)"))
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("version", equalTo("1.0.1"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // List the artifact versions
        given()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("count", equalTo(2))
                .body("versions[0].version", equalTo("1.0.0"))
                .body("versions[1].version", equalTo("1.0.1"));

        // Add version 1.0.2
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-Version", "1.0.2")
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .body(artifactContent.replace("Empty API", "Empty API (Version 1.0.2)"))
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("version", equalTo("1.0.2"))
                .body("type", equalTo(ArtifactType.OPENAPI));

        // List the artifact versions
        given()
                .when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
                .then()
                .statusCode(200)
                .body("count", equalTo(3))
                .body("versions[0].version", equalTo("1.0.0"))
                .body("versions[1].version", equalTo("1.0.1"))
                .body("versions[2].version", equalTo("1.0.2"));

    }

    @Test
    public void testCreateArtifactAfterDelete() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact - indicate the type via a header param
        createArtifact(GROUP, "testCreateArtifactAfterDelete/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Delete the artifact
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testCreateArtifactAfterDelete/EmptyAPI")
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(204);

        // Create the same artifact
        createArtifact(GROUP, "testCreateArtifactAfterDelete/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

    }

    @Test
    public void testCreateArtifactFromURL() throws Exception {
        // Create Artifact from URL should support `HEAD`
        given()
                .when()
                .contentType(CT_JSON_EXTENDED)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifactFromURL/Empty")
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .body("{ \"content\" : \"http://localhost:" + testPort + "/health/group\" }")
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(400)
                .body("message", containsString("Content-Length"));

        // Create Artifact from URL should check the SHA
        given()
                .when()
                .contentType(CT_JSON_EXTENDED)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifactFromURL/OpenApi2")
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .header("X-Registry-Content-Hash", "123")
                .body("{ \"content\" : \"http://localhost:" + testPort + "/api-specifications/registry/v2/openapi.json\" }")
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(400)
                .body("message", containsString("Hash doesn't match"));

        // Create Artifact from URL should fail if the algorithm is unsupported
        given()
                .when()
                .contentType(CT_JSON_EXTENDED)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifactFromURL/OpenApi2")
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .header("X-Registry-Hash-Algorithm", "ASH652")
                .header("X-Registry-Content-Hash", "123")
                .body("{ \"content\" : \"http://localhost:" + testPort + "/api-specifications/registry/v2/openapi.json\" }")
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(400)
                .body("message", containsString("hash algorithm not supported"));

        // Calculate the SHA on the fly to avoid mismatches on update
        String content = given()
                .get("http://localhost:" + testPort + "/api-specifications/registry/v2/openapi.json")
                .body()
                .print();
        String artifactSHA = Hashing.sha256().hashString(content, StandardCharsets.UTF_8).toString();

        // Create Artifact from URL should eventually succeed
        given()
                .when()
                .contentType(CT_JSON_EXTENDED)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", "testCreateArtifactFromURL/OpenApi3")
                .header("X-Registry-ArtifactType", ArtifactType.JSON)
                .header("X-Registry-Content-Hash", artifactSHA)
                .body("{ \"content\" : \"http://localhost:" + testPort + "/api-specifications/registry/v2/openapi.json\" }")
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200);
    }

    @Test
    void testArtifactWithReferences() throws Exception {
        String artifactContent = getRandomValidJsonSchemaContent();

        // Create #1 without references
        var response = createArtifactExtendedRaw("default", null, null, artifactContent, null);
        var metadata = response
                .statusCode(HTTP_OK)
                .extract().as(ArtifactMetaData.class);
        // Save the metadata for artifact #1 for later use
        var referencedMD = metadata;

        // Create #2 referencing the #1, using different content
        List<ArtifactReference> references = List.of(ArtifactReference.builder()
                .groupId(metadata.getGroupId())
                .artifactId(metadata.getId())
                .version(metadata.getVersion())
                .name("foo")
                .build());
        artifactContent = getRandomValidJsonSchemaContent();

        response = createArtifactExtendedRaw("default", null, null, artifactContent, references);

        metadata = response
                .statusCode(HTTP_OK)
                .extract().as(ArtifactMetaData.class);
        // Save the referencing artifact metadata for later use
        var referencingMD = metadata;
        assertEquals(references, metadata.getReferences());


        // Trying to use different references with the same content is ok, but the contentId and contentHash is different.
        List<ArtifactReference> references2 = List.of(ArtifactReference.builder()
                .groupId(metadata.getGroupId())
                .artifactId(metadata.getId())
                .version(metadata.getVersion())
                .name("foo2")
                .build());

        response = createArtifactExtendedRaw("default", null, null, artifactContent, references2);

        var secondMetadata = response
                .statusCode(HTTP_OK)
                .extract().as(ArtifactMetaData.class);

        assertNotEquals(secondMetadata.getContentId(), metadata.getContentId());

        // Same references are not an issue
        response = createArtifactExtendedRaw("default2", null, null, artifactContent, references);
        metadata = response
                .statusCode(HTTP_OK)
                .extract().as(ArtifactMetaData.class);

        // Get references via globalId
        var referenceResponse = given()
                .when()
                .pathParam("globalId", metadata.getGlobalId())
                .get("/registry/v2/ids/globalIds/{globalId}/references")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });

        assertEquals(references, referenceResponse);

        // Get references via contentId
        referenceResponse = given()
                .when()
                .pathParam("contentId", metadata.getContentId())
                .get("/registry/v2/ids/contentIds/{contentId}/references")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });

        final String referencesSerialized = SqlUtil.serializeReferences(toReferenceDtos(references));

        //We calculate the hash using the content itself and the references
        String contentHash = DigestUtils.sha256Hex(concatContentAndReferences(artifactContent.getBytes(StandardCharsets.UTF_8), referencesSerialized.getBytes(StandardCharsets.UTF_8)));

        assertEquals(references, referenceResponse);

        // Get references via contentHash
        referenceResponse = given()
                .when()
                .pathParam("contentHash", contentHash)
                .get("/registry/v2/ids/contentHashes/{contentHash}/references")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });

        assertEquals(references, referenceResponse);

        // Get references via GAV
        referenceResponse = given()
                .when()
                .pathParam("groupId", metadata.getGroupId())
                .pathParam("artifactId", metadata.getId())
                .pathParam("version", metadata.getVersion())
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/references")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });

        assertEquals(references, referenceResponse);

        // Get INBOUND references via GAV
        referenceResponse = given()
                .when()
                .pathParam("groupId", referencedMD.getGroupId() == null ? "default" : referencedMD.getGroupId())
                .pathParam("artifactId", referencedMD.getId())
                .pathParam("version", referencedMD.getVersion())
                .queryParam("refType", ReferenceType.INBOUND)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/references")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });
        assertFalse(referenceResponse.isEmpty());
        assertEquals(2, referenceResponse.size());
        assertEquals(referencingMD.getGroupId(), referenceResponse.get(0).getGroupId());
        assertEquals(referencingMD.getId(), referenceResponse.get(0).getArtifactId());
        assertEquals(referencingMD.getVersion(), referenceResponse.get(0).getVersion());

        // Get INBOUND references via globalId
        referenceResponse = given()
                .when()
                .pathParam("globalId", referencedMD.getGlobalId())
                .queryParam("refType", ReferenceType.INBOUND)
                .get("/registry/v2/ids/globalIds/{globalId}/references")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<ArtifactReference>>() {
                });
        assertFalse(referenceResponse.isEmpty());
        assertEquals(2, referenceResponse.size());
        assertEquals(referencingMD.getGroupId(), referenceResponse.get(0).getGroupId());
        assertEquals(referencingMD.getId(), referenceResponse.get(0).getArtifactId());
        assertEquals(referencingMD.getVersion(), referenceResponse.get(0).getVersion());
    }

    private byte[] concatContentAndReferences(byte[] contentBytes, byte[] referencesBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentBytes.length + referencesBytes.length);
        outputStream.write(contentBytes);
        outputStream.write(referencesBytes);
        return outputStream.toByteArray();
    }
    
    @Test
    public void testArtifactComments() throws Exception {
        String artifactId = "testArtifactComments/EmptyAPI";
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Get comments for the artifact (should be none)
        List<Comment> comments = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(0, comments.size());
        
        // Create a new comment
        NewComment nc = NewComment.builder().value("COMMENT_1").build();
        Comment comment1 = given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(nc)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(Comment.class);
        assertNotNull(comment1);
        assertNotNull(comment1.getCommentId());
        assertNotNull(comment1.getValue());
        assertNotNull(comment1.getCreatedOn());
        assertEquals("COMMENT_1", comment1.getValue());
        
        // Create another new comment
        nc = NewComment.builder().value("COMMENT_2").build();
        Comment comment2 = given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(nc)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(Comment.class);
        assertNotNull(comment2);
        assertNotNull(comment2.getCommentId());
        assertNotNull(comment2.getValue());
        assertNotNull(comment2.getCreatedOn());
        assertEquals("COMMENT_2", comment2.getValue());
        
        // Get the list of comments (should have 2)
        comments = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(2, comments.size());
        assertEquals("COMMENT_2", comments.get(0).getValue());
        assertEquals("COMMENT_1", comments.get(1).getValue());

        // Update a comment
        nc = NewComment.builder().value("COMMENT_2_UPDATED").build();
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("commentId", comment2.getCommentId())
                .body(nc)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments/{commentId}")
                .then()
                .statusCode(HTTP_NO_CONTENT);

        // Get the list of comments (should have 2)
        comments = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(2, comments.size());
        assertEquals("COMMENT_2_UPDATED", comments.get(0).getValue());
        assertEquals("COMMENT_1", comments.get(1).getValue());

        // Delete a comment
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("commentId", comment2.getCommentId())
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments/{commentId}")
                .then()
                .statusCode(HTTP_NO_CONTENT);

        // Get the list of comments (should have only 1)
        comments = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/latest/comments")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(new TypeRef<List<Comment>>() {
                });
        assertEquals(1, comments.size());
        assertEquals("COMMENT_1", comments.get(0).getValue());
    }

    @Test
    public void testCreateArtifactIntegrityRuleViolation() throws Exception {
        String artifactContent = resourceToString("jsonschema-valid.json");
        String artifactId = "testCreateArtifact/IntegrityRuleViolation";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, artifactContent);

        // Enable the Integrity rule for the artifact
        Rule rule = new Rule();
        rule.setType(RuleType.INTEGRITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules/INTEGRITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("INTEGRITY"))
                    .body("config", equalTo("FULL"));
        });

        // Now try registering an artifact with a valid reference
        InputStream data = new ByteArrayInputStream(artifactContent.getBytes(StandardCharsets.UTF_8));
        List<ArtifactReference> references = new ArrayList<>();
        references.add(ArtifactReference.builder()
                .groupId(GROUP)
                .artifactId(artifactId)
                .version("1")
                .name("other.json#/defs/Foo")
                .build());
        clientV2.updateArtifact(GROUP, artifactId, "2", null, null, data, references);

        // Now try registering an artifact with an INVALID reference
        data = new ByteArrayInputStream(artifactContent.getBytes(StandardCharsets.UTF_8));
        references = new ArrayList<>();
        references.add(ArtifactReference.builder()
                .groupId(GROUP)
                .artifactId("ArtifactThatDoesNotExist")
                .version("1")
                .name("other.json#/defs/Foo")
                .build());
        final InputStream dataf_1 = data;
        final List<ArtifactReference> referencesf_1 = references;
        Assertions.assertThrows(RuleViolationException.class, () -> {
            clientV2.updateArtifact(GROUP, artifactId, "2", null, null, dataf_1, referencesf_1);
        });

        // Now try registering an artifact with both a valid and invalid ref
        data = new ByteArrayInputStream(artifactContent.getBytes(StandardCharsets.UTF_8));
        references = new ArrayList<>();
        // valid ref
        references.add(ArtifactReference.builder()
                .groupId(GROUP)
                .artifactId(artifactId)
                .version("1")
                .name("other.json#/defs/Foo")
                .build());
        // invalid ref
        references.add(ArtifactReference.builder()
                .groupId(GROUP)
                .artifactId("ArtifactThatDoesNotExist")
                .version("1")
                .name("other.json#/defs/Bar")
                .build());
        final InputStream dataf_2 = data;
        final List<ArtifactReference> referencesf_2 = references;
        Assertions.assertThrows(RuleViolationException.class, () -> {
            clientV2.updateArtifact(GROUP, artifactId, "2", null, null, dataf_2, referencesf_2);
        });

        // Now try registering an artifact with a duplicate ref
        data = new ByteArrayInputStream(artifactContent.getBytes(StandardCharsets.UTF_8));
        references = new ArrayList<>();
        // valid ref
        references.add(ArtifactReference.builder()
                .groupId(GROUP)
                .artifactId(artifactId)
                .version("1")
                .name("other.json#/defs/Foo")
                .build());
        // duplicate ref
        references.add(ArtifactReference.builder()
                .groupId(GROUP)
                .artifactId(artifactId)
                .version("1")
                .name("other.json#/defs/Foo")
                .build());
        final InputStream dataf_3 = data;
        final List<ArtifactReference> referencesf_3 = references;
        Assertions.assertThrows(RuleViolationException.class, () -> {
            clientV2.updateArtifact(GROUP, artifactId, "2", null, null, dataf_3, referencesf_3);
        });

    }


    @Test
    public void testGetArtifactVersionWithReferences() throws Exception {
        String referencedTypesContent = resourceToString("referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testGetArtifactVersionWithReferences/ReferencedTypes", ArtifactType.OPENAPI, referencedTypesContent);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(
                ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget")
                .groupId(GROUP)
                .artifactId("testGetArtifactVersionWithReferences/ReferencedTypes")
                .version("1")
                .build());
        createArtifactWithReferences(GROUP, "testGetArtifactVersionWithReferences/WithExternalRef", ArtifactType.OPENAPI, withExternalRefContent, refs);
        
        // Get the content of the artifact preserving external references
        given()
        .when()
            .pathParam("groupId", GROUP)
            .pathParam("artifactId", "testGetArtifactVersionWithReferences/WithExternalRef")
        .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
        .then()
            .statusCode(200)
            .body("openapi", equalTo("3.0.2"))
            .body("paths.widgets.get.responses.200.content.json.schema.items.$ref", equalTo("./referenced-types.json#/components/schemas/Widget"));
        
        // Get the content of the artifact rewriting external references
        given()
        .when()
            .pathParam("groupId", GROUP)
            .pathParam("artifactId", "testGetArtifactVersionWithReferences/WithExternalRef")
            .queryParam("references", "REWRITE")
        .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
        .then()
            .statusCode(200)
            .body("openapi", equalTo("3.0.2"))
            .body("paths.widgets.get.responses.200.content.json.schema.items.$ref", endsWith("/apis/registry/v2/groups/GroupsResourceTest/artifacts/testGetArtifactVersionWithReferences%2FReferencedTypes/versions/1?references=REWRITE#/components/schemas/Widget"));
        
        // Get the content of the artifact inlining/dereferencing external references
        given()
        .when()
            .pathParam("groupId", GROUP)
            .pathParam("artifactId", "testGetArtifactVersionWithReferences/WithExternalRef")
            .queryParam("references", "DEREFERENCE")
        .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
        .then()
            .statusCode(200)
            .body("openapi", equalTo("3.0.2"))
            .body("paths.widgets.get.responses.200.content.json.schema.items.$ref", equalTo("#/components/schemas/Widget"));
    }

}
