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

package io.apicurio.tests.smokeTests;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.HttpUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.tests.utils.TestUtils.ruleToString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

class RulesResourceIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);

    @Test
    void createAndValidateGlobalRules() {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        String invalidRule = "{\"type\":\"INVALID\",\"config\":\"invalid\"}";

        HttpUtils.createGlobalRule(ruleToString(rule));
        LOGGER.info("Created rule: {} - {}", rule.getType(), rule.getConfig());

        LOGGER.info("Creating invalid rule");
        HttpUtils.createGlobalRule(invalidRule, 400);

        HttpUtils.createGlobalRule(ruleToString(rule), 409);

        String invalidArtifact = "<type>record</type>\n<name>test</name>";
        HttpUtils.createArtifact(invalidArtifact, 400);

        Schema artifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        Response response = HttpUtils.createArtifact(artifact.toString());
        String artifactId = response.jsonPath().getString("id");

        // According documentation, this should return 404 @TODO ewittmann
        HttpUtils.updateArtifact(artifactId, invalidArtifact, 400);

        Schema updatedArtifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}");
        response = HttpUtils.updateArtifact(artifactId, updatedArtifact.toString());
        JsonPath jsonPath = response.jsonPath();
        LOGGER.info("Schema with ID {} was updated: {}", artifactId, jsonPath.get());

        response = HttpUtils.listArtifactVersions(artifactId);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2));
    }

    @Test
    void createAndValidateArtifactRule() {
        Schema artifact1 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        Response response = HttpUtils.createArtifact(artifact1.toString());
        String artifactId1 = response.jsonPath().getString("id");
        LOGGER.info("Created artifact {} with ID {}", "myrecord1", artifactId1);

        Schema artifact2 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        response = HttpUtils.createArtifact(artifact1.toString());
        String artifactId2 = response.jsonPath().getString("id");
        LOGGER.info("Created artifact {} with ID {}", "myrecord1", artifactId2);

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");

        String invalidRule = "{\"type\":\"INVALID\",\"config\":\"invalid\"}";

        HttpUtils.createArtifactRule(artifactId1, ruleToString(rule));
        LOGGER.info("Created rule: {} - {} for artifact {}", rule.getType(), rule.getConfig(), artifactId1);

        LOGGER.info("Creating invalid rule");
        HttpUtils.createArtifactRule(artifactId1, invalidRule, 400);

        HttpUtils.createArtifactRule(artifactId1, ruleToString(rule), 409);

        String invalidArtifact = "<type>record</type>\n<name>test</name>";
        // According documentation, this should return 404 @TODO ewittmann
        HttpUtils.updateArtifact(artifactId1, invalidArtifact, 400);

        response = HttpUtils.updateArtifact(artifactId2, invalidArtifact);
        JsonPath jsonPath = response.jsonPath();
        LOGGER.info("Schema with ID {} was updated: {}", artifactId1, jsonPath.get());

        Schema updatedArtifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}");
        response = HttpUtils.updateArtifact(artifactId1, updatedArtifact.toString());
        jsonPath = response.jsonPath();
        LOGGER.info("Schema with ID {} was updated: {}", artifactId1, jsonPath.get());

        response = HttpUtils.listArtifactVersions(artifactId1);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of artifact with ID {} are: {}", artifactId1, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2));
    }
}
