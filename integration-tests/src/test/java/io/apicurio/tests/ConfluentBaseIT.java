package io.apicurio.tests;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.utils.Constants;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class ConfluentBaseIT extends ApicurioRegistryBaseIT {

    protected SchemaRegistryClient confluentService;

    @BeforeAll
    void confluentBeforeAll(TestInfo info) throws Exception {
        confluentService = new CachedSchemaRegistryClient(
                ApicurioRegistryBaseIT.getRegistryApiUrl() + "/ccompat/v7", 3);
        clearAllConfluentSubjects();
    }

    @AfterEach
    void clear() throws IOException, RestClientException {
        clearAllConfluentSubjects();
    }

    public int createArtifactViaConfluentClient(ParsedSchema schema, String artifactName)
            throws IOException, RestClientException, TimeoutException {
        int idOfSchema = confluentService.register(artifactName, schema);
        confluentService.reset(); // clear cache
        TestUtils.waitFor("Wait until artifact globalID mapping is finished", Constants.POLL_INTERVAL,
                Constants.TIMEOUT_GLOBAL, () -> {
                    try {
                        ParsedSchema newSchema = confluentService.getSchemaBySubjectAndId(artifactName,
                                idOfSchema);
                        logger.info("Checking that created schema is equal to the get schema");
                        assertThat(schema.toString(), is(newSchema.toString()));
                        assertThat(confluentService.getVersion(artifactName, schema),
                                is(confluentService.getVersion(artifactName, newSchema)));
                        logger.info("Created schema with id:{} and name:{}", idOfSchema, newSchema.name());
                        return true;
                    } catch (IOException | RestClientException e) {
                        logger.debug("", e);
                        return false;
                    }
                });
        return idOfSchema;
    }

    protected void clearAllConfluentSubjects() throws IOException, RestClientException {
        if (confluentService != null && !confluentService.getAllSubjects().isEmpty()) {
            for (String confluentSubject : confluentService.getAllSubjects()) {
                if (confluentSubject != null) {
                    logger.info("Deleting confluent schema {}", confluentSubject);
                    try {
                        registryClient.groups().byGroupId("default").artifacts().byArtifactId(confluentSubject).delete();
                    } catch (Exception e) {
                        System.out.println("WARNING>> Failed to delete confluent schema " + confluentSubject);
                        System.out.println("WARNING>>    " + e.getMessage());
                    }
                }
            }
        }
    }

    protected void waitForSubjectDeleted(String subjectName) throws Exception {
        TestUtils.retry(() -> {
            try {
                confluentService.getAllVersions(subjectName);
            } catch (IOException e) {
                logger.warn("", e);
                throw e;
            } catch (RestClientException e) {
                assertEquals(404, e.getStatus());
                return;
            }
            throw new IllegalStateException("Subject " + subjectName + "has not been deleted yet");
        });
    }

}
