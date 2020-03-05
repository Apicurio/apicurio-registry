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
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
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
    void createAndUpdateSchema() throws IOException, RestClientException {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo1\",\"type\":\"string\"}]}");

        int idOfSchema = confluentService.register(SUBJECT_NAME, schema);

        LOGGER.info("Schema with ID {} was created: {}", idOfSchema, schema.toString());

        Schema newSchema = confluentService.getBySubjectAndId(SUBJECT_NAME, idOfSchema);

        LOGGER.info("Checking that created schema is equal to the get schema");
        assertThat(schema.toString(), is(newSchema.toString()));
        assertThat(confluentService.getVersion(SUBJECT_NAME, schema), is(confluentService.getVersion(SUBJECT_NAME, newSchema)));

        assertThat("myrecord1", is(newSchema.getFullName()));
        assertThat(Schema.Type.RECORD, is(newSchema.getType()));
        assertThat("foo1 type:STRING pos:0", is(newSchema.getFields().get(0).toString()));

        Schema updatedSchema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo2\",\"type\":\"long\"}]}");

        idOfSchema = confluentService.register(SUBJECT_NAME, updatedSchema);

        LOGGER.info("Schema with ID {} was updated: {}", idOfSchema, updatedSchema.toString());

        Schema newUpdatedSchema = confluentService.getBySubjectAndId(SUBJECT_NAME, idOfSchema);

        LOGGER.info("Checking that created schema is equal to the get schema");
        assertThat(updatedSchema.toString(), is(newUpdatedSchema.toString()));
        assertThat(confluentService.getVersion(SUBJECT_NAME, updatedSchema), is(confluentService.getVersion(SUBJECT_NAME, newUpdatedSchema)));

        assertThat("myrecord2", is(newUpdatedSchema.getFullName()));
        assertThat(Schema.Type.RECORD, is(newUpdatedSchema.getType()));
        assertThat("foo2 type:LONG pos:0", is(newUpdatedSchema.getFields().get(0).toString()));

        assertThrows(SchemaParseException.class, () -> new Schema.Parser().parse("<type>record</type>\n<name>test</name>"));

        confluentService.deleteSubject(SUBJECT_NAME);
    }

    @Test
    void createAndDeleteMultipleSchemas() throws IOException, RestClientException {
        for (int i = 0; i < 50; i++) {
            String name = "myrecord" + i;
            String subjectName = "subject-example-" + i;
            Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
            int idOfSchema = confluentService.register(subjectName, schema);
            Schema newSchema  = confluentService.getById(idOfSchema);
            LOGGER.info("Created schema with id:{} and name:{}", idOfSchema, newSchema.getFullName());
        }

        assertThat(50, is(confluentService.getAllSubjects().size()));
        LOGGER.info("All subjects {} schemas", confluentService.getAllSubjects().size());

        for (int i = 0; i < 50; i++) {
            confluentService.deleteSubject("subject-example-" + i);
        }

        assertThat(0, is(confluentService.getAllSubjects().size()));
        LOGGER.info("All subjects {} schemas", confluentService.getAllSubjects().size());
    }

    @Test
    void deleteSchemasSpecificVersion() throws IOException, RestClientException {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"mynewrecord\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");

        int schemaId = confluentService.register(SUBJECT_NAME, schema);

        LOGGER.info("Created record with name: {} and ID: {}", schema.getFullName(), schemaId);

        String schemaSubject = "subject-example";

        for (int x = 0; x < 1; x++) {
            schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo" + x + "\",\"type\":\"string\"}]}");
            confluentService.register(schemaSubject, schema);
        }

        List<Integer> schemeVersions = confluentService.getAllVersions(schemaSubject);

        LOGGER.info("Available version of schema with Id:{} are {}", schemaId, schemeVersions);
        assertThat(schemeVersions, hasItems(1, 2));

        confluentService.deleteSchemaVersion(schemaSubject, "2");

        schemeVersions = confluentService.getAllVersions(schemaSubject);

        LOGGER.info("Available version of schema with Id:{} are {}", schemaId, schemeVersions);
        assertThat(schemeVersions, hasItems(1));

        try {
            confluentService.getVersion("2", schema);
        } catch (RestClientException e) {
            LOGGER.info("Schema version 2 doesn't exists in subject {}", schemaSubject);
        }

        schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo" + 4 + "\",\"type\":\"string\"}]}");

        confluentService.register(schemaSubject, schema);

        schemeVersions = confluentService.getAllVersions(schemaSubject);

        LOGGER.info("Available version of schema with Id:{} are {}", schemaId, schemeVersions);
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
    void createSchemaSpecifyVersion() throws IOException, RestClientException {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");

        int schemaId = confluentService.register(SUBJECT_NAME, schema);

        schema = confluentService.getById(schemaId);

        LOGGER.info("Created artifact name:{} with Id:{}", schema.getFullName(), schemaId);

        Schema updatedArtifact = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        schemaId = confluentService.register(SUBJECT_NAME, updatedArtifact);

        schema = confluentService.getById(schemaId);

        LOGGER.info("Schema with name:{} Id:{} was updated content:{}", schema.getFullName(), schemaId, schema.toString());

        List<Integer> schemaVersions = confluentService.getAllVersions(SUBJECT_NAME);

        LOGGER.info("Available versions of schema with ID {} are: {}", schemaId, schemaVersions.toString());
        assertThat(schemaVersions, hasItems(1, 2));

        confluentService.deleteSubject(SUBJECT_NAME);
    }

    @AfterEach
    void tearDown() throws IOException, RestClientException {
        confluentService.deleteSubject(SUBJECT_NAME);
    }
}
