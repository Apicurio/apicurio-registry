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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SmokeIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeIT.class);

    @Test
    void createAndUpdateSchemas() {
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");

        Response response = HttpUtils.createGlobalRule(rule);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        response = HttpUtils.createSchema(schema.toString());
        JsonPath jsonPath = response.jsonPath();
        String schemaId = jsonPath.getString("id");
        LOGGER.info("Schema with ID {} was created: {}", schemaId, jsonPath.get());

        String wrongSchema = "<type>record</type>\n<name>test</name>";
        response = HttpUtils.createSchema(wrongSchema, 400);
        jsonPath = response.jsonPath();
        LOGGER.info("Invalid schema sent: {}", jsonPath);

        response = HttpUtils.getSchema(schemaId);
        jsonPath = response.jsonPath();
        LOGGER.info("Got info about schema with ID {}: {}", schemaId, jsonPath.get());

        Schema updatedSchema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"long\"}]}");
        response = HttpUtils.updateSchema(schemaId, updatedSchema.toString());
        jsonPath = response.jsonPath();
        LOGGER.info("Schema with ID {} was updated: {}", schemaId, jsonPath.get());

        response = HttpUtils.getSchema(schemaId);
        jsonPath = response.jsonPath();
        LOGGER.info("Got info about schema with ID {}: {}", schemaId, jsonPath.get());
        assertThat(HttpUtils.getFieldsFromResponse(jsonPath).get("name"), is("bar"));

        response = HttpUtils.listSchemaVersions(schemaId);
        jsonPath = response.jsonPath();
        LOGGER.info("Available versions of schema with ID {} are: {}", schemaId, jsonPath.get());
        assertThat(jsonPath.get(), hasItems(1, 2));

        response = HttpUtils.getSchemaSpecificVersion(schemaId, "1");
        jsonPath = response.jsonPath();
        LOGGER.info("Schema with ID {} and version {}: {}", schemaId, "1", jsonPath.get());
        assertThat(HttpUtils.getFieldsFromResponse(jsonPath).get("name"), is("foo"));
    }
}

