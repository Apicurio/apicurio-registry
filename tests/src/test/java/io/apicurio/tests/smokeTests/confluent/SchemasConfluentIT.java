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

package io.apicurio.tests.smokeTests.confluent;

import io.apicurio.tests.BaseIT;
import io.apicurio.tests.Constants;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import io.apicurio.tests.utils.subUtils.TestUtils;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.restassured.response.Response;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.apicurio.tests.Constants.SMOKE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag(SMOKE)
public class SchemasConfluentIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemasConfluentIT.class);
    private static final String SUBJECT_NAME = "subject-example";

    @Test
    void createAndUpdateSchema() throws IOException, RestClientException, TimeoutException {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo1\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, SUBJECT_NAME);

        Schema updatedSchema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo2\",\"type\":\"long\"}]}");
        createArtifactViaConfluentClient(updatedSchema, SUBJECT_NAME);

        assertThrows(SchemaParseException.class, () -> new Schema.Parser().parse("<type>record</type>\n<name>test</name>"));
        assertThat(confluentService.getAllVersions(SUBJECT_NAME).size(), is(2));

        confluentService.deleteSubject(SUBJECT_NAME);
    }

    @Test
    void createAndDeleteMultipleSchemas() throws IOException, RestClientException, TimeoutException {
        for (int i = 0; i < 50; i++) {
            String name = "myrecord" + i;
            String subjectName = "subject-example-" + i;
            Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
            createArtifactViaConfluentClient(schema, subjectName);
        }

        assertThat(50, is(confluentService.getAllSubjects().size()));
        LOGGER.info("All subjects {} schemas", confluentService.getAllSubjects().size());

        for (int i = 0; i < 50; i++) {
            confluentService.deleteSubject("subject-example-" + i);
        }

        TestUtils.waitFor("all schemas deletion", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return confluentService.getAllSubjects().size() == 0;
            } catch (IOException | RestClientException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    @Test
    void deleteSchemasSpecificVersion() throws IOException, RestClientException, TimeoutException {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"mynewrecord\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, SUBJECT_NAME);
        schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo1\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, SUBJECT_NAME);

        List<Integer> schemeVersions = confluentService.getAllVersions(SUBJECT_NAME);

        LOGGER.info("Available version of schema with name:{} are {}", SUBJECT_NAME, schemeVersions);
        assertThat(schemeVersions, hasItems(1, 2));

        confluentService.deleteSchemaVersion(SUBJECT_NAME, "2");

        schemeVersions = confluentService.getAllVersions(SUBJECT_NAME);

        LOGGER.info("Available version of schema with name:{} are {}", SUBJECT_NAME, schemeVersions);
        assertThat(schemeVersions, hasItems(1));

        try {
            confluentService.getVersion("2", schema);
        } catch (RestClientException e) {
            LOGGER.info("Schema version 2 doesn't exists in subject {}", SUBJECT_NAME);
        }

        schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo" + 4 + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, SUBJECT_NAME);

        schemeVersions = confluentService.getAllVersions(SUBJECT_NAME);

        LOGGER.info("Available version of schema with name:{} are {}", SUBJECT_NAME, schemeVersions);
        assertThat(schemeVersions, hasItems(1, 2));

        confluentService.deleteSubject(SUBJECT_NAME);
    }

    @Test
    void createInvalidSchemaDefinition() {
        String invalidSchemaDefinition = "{\"type\":\"INVALID\",\"config\":\"invalid\"}";

        Response response = ArtifactUtils.createSchema(invalidSchemaDefinition, "name-of-schema-example", 400);

        assertThat("Unrecognized field &quot;type&quot; (class io.apicurio.registry.ccompat.dto.RegisterSchemaRequest), not marked as ignorable", is(response.body().print()));
    }

    @Test
    void createSchemaSpecifyVersion() throws IOException, RestClientException, TimeoutException {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, SUBJECT_NAME);

        Schema updatedArtifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(updatedArtifact, SUBJECT_NAME);

        List<Integer> schemaVersions = confluentService.getAllVersions(SUBJECT_NAME);

        LOGGER.info("Available versions of schema with NAME {} are: {}", SUBJECT_NAME, schemaVersions.toString());
        assertThat(schemaVersions, hasItems(1, 2));

        confluentService.deleteSubject(SUBJECT_NAME);
    }

    @Test
    void deleteNonexistingSchema() {
        assertThrows(RestClientException.class, () -> confluentService.deleteSubject("non-existing"));
    }

    @AfterEach
    void tearDown() throws IOException, RestClientException {
        clearAllConfluentSubjects();
    }
}
