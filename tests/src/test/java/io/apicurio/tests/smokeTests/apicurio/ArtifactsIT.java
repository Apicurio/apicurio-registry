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
package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import io.apicurio.tests.utils.subUtils.GlobalRuleUtils;
import io.apicurio.tests.utils.subUtils.TestUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.avro.Schema;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class ArtifactsIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactsIT.class);

    @Test
    void createAndUpdateArtifact() {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        Response response = GlobalRuleUtils.createGlobalRule(TestUtils.ruleToString(rule));

        Schema artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        response = ArtifactUtils.createArtifact(artifact.toString());
        JsonPath jsonPath = response.jsonPath();
        String artifactId = jsonPath.getString("id");
        LOGGER.info("Artifact with ID {} was created: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("createdOn"), notNullValue());

        String invalidArtifact = "<type>record</type>\n<name>test</name>";
        response = ArtifactUtils.createArtifact(invalidArtifact, 400);
        jsonPath = response.jsonPath();
        LOGGER.info("Invalid artifact sent: {}", (Object) jsonPath.get());
        assertThat(jsonPath.get("message"), is("Syntax violation for Avro artifact."));

        response = ArtifactUtils.getArtifact(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("name"), is("myrecord1"));

        Schema updatedArtifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}");
        response = ArtifactUtils.updateArtifact(artifactId, updatedArtifact.toString());
        jsonPath = response.jsonPath();
        LOGGER.info("Schema with ID {} was updated: {}", artifactId, jsonPath.get());

        response = ArtifactUtils.getArtifact(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, jsonPath.get());
        assertThat(ArtifactUtils.getFieldsFromResponse(jsonPath).get("name"), is("bar"));

        response = ArtifactUtils.listArtifactVersions(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2));

        response = ArtifactUtils.getArtifactSpecificVersion(artifactId, "1");
        jsonPath = response.jsonPath();
        LOGGER.info("Artifact with ID {} and version {}: {}", artifactId, "1", jsonPath.get());
        assertThat(ArtifactUtils.getFieldsFromResponse(jsonPath).get("name"), is("foo"));
    }

    @Test
    void createAndDeleteMultipleArtifacts() {
        LOGGER.info("Creating some artifacts...");
        Map<String, String> idMap = createMultipleArtifacts(10);
        LOGGER.info("Created  {} artifacts", idMap.size());

        deleteMultipleArtifacts(idMap);

        for (Map.Entry entry : idMap.entrySet()) {
            ArtifactUtils.getArtifact(entry.getValue().toString(), 404);
        }
    }

    @Test
    void deleteArtifactSpecificVersion() {
        String name = "myrecordx";
        Schema artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        Response response = ArtifactUtils.createArtifact(artifact.toString());
        JsonPath jsonPath = response.jsonPath();
        String artifactId = jsonPath.getString("id");
        LOGGER.info("Created record with name: {} and ID: {}", name, artifactId);

        for (int x = 0; x < 9; x++) {
            artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo" + x + "\",\"type\":\"string\"}]}");
            ArtifactUtils.updateArtifact(artifactId, artifact.toString());
        }

        response = ArtifactUtils.listArtifactVersions(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

        ArtifactUtils.deleteArtifactVersion(artifactId, "4");
        LOGGER.info("Version 4 of artifact {} was deleted", artifactId);

        response = ArtifactUtils.listArtifactVersions(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2, 3, 5, 6, 7, 8, 9, 10));
        assertThat(jsonPath.get(), not(hasItems(4)));

        response = ArtifactUtils.getArtifactSpecificVersion(artifactId, "4", 404);
        jsonPath = response.jsonPath();
        assertThat(jsonPath.get("message"), is("No version '4' found for artifact with ID '" + artifactId + "'."));

        artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo" + 11 + "\",\"type\":\"string\"}]}");
        ArtifactUtils.updateArtifact(artifactId, artifact.toString());

        response = ArtifactUtils.listArtifactVersions(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2, 3, 5, 6, 7, 8, 9, 10, 11));
        assertThat(jsonPath.get(), not(hasItems(4)));

    }

    @Test
    void createNonAvroArtifact() {
        String invalidRule = "{\"type\":\"INVALID\",\"config\":\"invalid\"}";
        Response response = ArtifactUtils.createArtifact(invalidRule);
        String artifactId = response.jsonPath().getString("id");
        LOGGER.info("Created artifact {} with ID {}: {}", "myrecord1", artifactId, response.jsonPath().get());

        response = ArtifactUtils.getArtifact(artifactId);
        JsonPath jsonPath = response.jsonPath();
        LOGGER.info("Got info about artifact with ID {}: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get("type"), is("INVALID"));
        assertThat(jsonPath.get("config"), is("invalid"));
    }

    @Test
    void createArtifactSpecificVersion() {
        Schema artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        Response response = ArtifactUtils.createArtifact(artifact.toString());
        String artifactId = response.jsonPath().getString("id");
        LOGGER.info("Created artifact {} with ID {}", "myrecord1", artifactId);

        Schema updatedArtifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        response = ArtifactUtils.createArtifactNewVersion(artifactId, updatedArtifact.toString(), 200);
        JsonPath jsonPath = response.jsonPath();
        LOGGER.info("Schema with ID {} was updated: {}", artifactId, jsonPath.get());

        response = ArtifactUtils.listArtifactVersions(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2));
    }

    @AfterEach
    void deleteRules() {
        GlobalRuleUtils.deleteAllGlobalRules();
    }
}

