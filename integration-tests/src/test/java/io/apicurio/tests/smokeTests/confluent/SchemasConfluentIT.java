package io.apicurio.tests.smokeTests.confluent;

import io.apicurio.tests.ConfluentBaseIT;
import io.apicurio.tests.utils.ArtifactUtils;
import io.apicurio.tests.utils.Constants;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import org.apache.avro.SchemaParseException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.apicurio.tests.utils.Constants.SMOKE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag(SMOKE)
@QuarkusIntegrationTest
public class SchemasConfluentIT extends ConfluentBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemasConfluentIT.class);

    @Test
    @Tag(ACCEPTANCE)
    void createAndUpdateSchema() throws Exception {
        String artifactId = TestUtils.generateArtifactId();

        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo1\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, artifactId);

        ParsedSchema updatedSchema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"foo2\",\"type\":\"long\"}]}");
        createArtifactViaConfluentClient(updatedSchema, artifactId);

        assertThrows(SchemaParseException.class, () -> new AvroSchema("<type>record</type>\n<name>test</name>"));
        assertThat(confluentService.getAllVersions(artifactId), hasItems(1, 2));

        confluentService.deleteSubject(artifactId);
        confluentService.deleteSubject(artifactId, true);
        waitForSubjectDeleted(artifactId);
    }

    @Test
    void createAndDeleteMultipleSchemas() throws IOException, RestClientException, TimeoutException {
        String prefix = TestUtils.generateArtifactId();

        for (int i = 0; i < 50; i++) {
            String name = "myrecord" + i;
            String subjectName = prefix + i;
            ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
            createArtifactViaConfluentClient(schema, subjectName);
        }

        assertThat(50, is(confluentService.getAllSubjects().size()));
        LOGGER.info("All subjects {} schemas", confluentService.getAllSubjects().size());

        for (int i = 0; i < 50; i++) {
            confluentService.deleteSubject(prefix + i);
        }

        TestUtils.waitFor("all schemas deletion", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return confluentService.getAllSubjects().size() == 0;
            } catch (IOException | RestClientException e) {
                return false;
            }
        });
    }

    @Test
    void deleteSchemasSpecificVersion() throws Exception {
        String artifactId = TestUtils.generateArtifactId();

        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"mynewrecord\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, artifactId);
        schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo1\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, artifactId);

        List<Integer> schemeVersions = confluentService.getAllVersions(artifactId);

        LOGGER.info("Available version of schema with name:{} are {}", artifactId, schemeVersions);
        assertThat(schemeVersions, hasItems(1, 2));

        schemeVersions = confluentService.getAllVersions(artifactId);

        LOGGER.info("Available version of schema with name:{} are {}", artifactId, schemeVersions);
        assertThat(schemeVersions, hasItems(1));

        schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo" + 4 + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, artifactId);

        confluentService.deleteSchemaVersion(artifactId, "2");
        confluentService.deleteSchemaVersion(artifactId, "2", true);


        TestUtils.waitFor("all specific schema version deletion", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return confluentService.getAllVersions(artifactId).size() == 2;
            } catch (IOException | RestClientException e) {
                return false;
            }
        });

        schemeVersions = confluentService.getAllVersions(artifactId);

        LOGGER.info("Available version of schema with name:{} are {}", artifactId, schemeVersions);
        assertThat(schemeVersions, hasItems(1, 3));

        confluentService.deleteSubject(artifactId);
        confluentService.deleteSubject(artifactId, true);
        waitForSubjectDeleted(artifactId);
    }

    @Test
    void createSchemaSpecifyVersion() throws Exception {
        String artifactId = TestUtils.generateArtifactId();

        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, artifactId);

        ParsedSchema updatedArtifact = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(updatedArtifact, artifactId);

        List<Integer> schemaVersions = confluentService.getAllVersions(artifactId);

        LOGGER.info("Available versions of schema with NAME {} are: {}", artifactId, schemaVersions.toString());
        assertThat(schemaVersions, hasItems(1, 2));

        confluentService.deleteSubject(artifactId);
        confluentService.deleteSubject(artifactId, true);
        waitForSubjectDeleted(artifactId);
    }

    @Test
    void deleteNonexistingSchema() {
        assertThrows(RestClientException.class, () -> confluentService.deleteSubject("non-existing"));
    }

    @Test
    void createInvalidSchemaDefinition() throws Exception {
        String subjectName = TestUtils.generateArtifactId();
        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, subjectName);

        TestUtils.waitFor("artifactCreated", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            return registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).get() != null;
        });

        String invalidSchema = "{\"schema\":\"{\\\"type\\\": \\\"bloop\\\"}\"}";

        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).rules().post(rule);

        TestUtils.waitFor("artifact rule created", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                Rule r = registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).rules().byRule(RuleType.COMPATIBILITY.name()).get();
                return r != null && r.getConfig() != null && r.getConfig().equalsIgnoreCase("BACKWARD");
            } catch (WebApplicationException e) {
                return false;
            }
        });
        ConfluentSubjectsUtils.createSchema(invalidSchema, subjectName, 422);
  }

    @Test
    void createConfluentQueryApicurio() throws IOException, RestClientException, TimeoutException {
        String name = "schemaname";
        String subjectName = TestUtils.generateArtifactId();
        String rawSchema = "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        ParsedSchema schema = new AvroSchema(rawSchema);
        createArtifactViaConfluentClient(schema, subjectName);

        assertThat(1, is(confluentService.getAllSubjects().size()));

        Response ar = ArtifactUtils.getArtifact("default", subjectName, "branch=latest", 200);
        assertEquals(rawSchema, ar.asString());
        LOGGER.info(ar.asString());

        TestUtils.waitFor("artifact created", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).versions().byVersionExpression("branch=latest").get().readAllBytes().length > 0;
            } catch (WebApplicationException e) {
                return false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testCreateDeleteSchemaRuleIsDeleted() throws Exception {

        String name = "schemaname";
        String subjectName = TestUtils.generateArtifactId();
        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        long contentId = createArtifactViaConfluentClient(schema, subjectName);

        assertThat(1, is(confluentService.getAllSubjects().size()));

        TestUtils.waitFor("waiting for content to be created", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return registryClient.ids().contentIds().byContentId(contentId).get().readAllBytes().length > 0;
            } catch (IOException cnfe) {
                return false;
            }
        });

        TestUtils.waitFor("artifact created", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).versions().byVersionExpression("branch=latest").get().readAllBytes().length > 0;
            } catch (WebApplicationException e) {
                return false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).rules().post(rule);

        TestUtils.waitFor("artifact rule created", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                Rule r = registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).rules().byRule(RuleType.VALIDITY.name()).get();
                return r != null && r.getConfig() != null && r.getConfig().equalsIgnoreCase("FULL");
            } catch (WebApplicationException e) {
                return false;
            }
        });

        List<RuleType> rules = registryClient.groups().byGroupId("default").artifacts().byArtifactId(subjectName).rules().get();
        assertThat(1, is(rules.size()));

        confluentService.deleteSubject(subjectName);

        TestUtils.waitFor("schema deletion", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return confluentService.getAllSubjects().size() == 0;
            } catch (IOException | RestClientException e) {
                return false;
            }
        });

        confluentService.deleteSubject(subjectName, true);

        retryOp((rc) -> {
            TestUtils.assertClientError("ArtifactNotFoundException", 404, () -> rc.groups().byGroupId("default").artifacts().byArtifactId(subjectName).get(), errorCodeExtractor);
            TestUtils.assertClientError("ArtifactNotFoundException", 404, () -> rc.groups().byGroupId("default").artifacts().byArtifactId(subjectName).rules().get(), errorCodeExtractor);
            TestUtils.assertClientError("ArtifactNotFoundException", 404, () -> rc.groups().byGroupId("default").artifacts().byArtifactId(subjectName).rules().byRule(rules.get(0).name()).get(), errorCodeExtractor);
        });
        //if rule was actually deleted creating same artifact again shouldn't fail
        createArtifactViaConfluentClient(schema, subjectName);
        assertThat(1, is(confluentService.getAllSubjects().size()));
    }

}
