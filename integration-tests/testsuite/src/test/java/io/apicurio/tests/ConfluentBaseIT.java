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
package io.apicurio.tests;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.Constants;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;

public abstract class ConfluentBaseIT extends ApicurioV2BaseIT {

    protected SchemaRegistryClient confluentService;

    @BeforeAll
    void confluentBeforeAll(TestInfo info) throws Exception {
        confluentService = new CachedSchemaRegistryClient(TestUtils.getRegistryApiUrl() + "/ccompat/v6", 3);
        clearAllConfluentSubjects();
    }

    @AfterEach
    void clear() throws IOException, RestClientException {
        clearAllConfluentSubjects();
    }

    public int createArtifactViaConfluentClient(ParsedSchema schema, String artifactName) throws IOException, RestClientException, TimeoutException {
        int idOfSchema = confluentService.register(artifactName, schema);
        confluentService.reset(); // clear cache
        TestUtils.waitFor("Wait until artifact globalID mapping is finished", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL,
            () -> {
                try {
                    ParsedSchema newSchema = confluentService.getSchemaBySubjectAndId(artifactName, idOfSchema);
                    logger.info("Checking that created schema is equal to the get schema");
                    assertThat(schema.toString(), is(newSchema.toString()));
                    assertThat(confluentService.getVersion(artifactName, schema), is(confluentService.getVersion(artifactName, newSchema)));
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
        for (String confluentSubject : confluentService.getAllSubjects()) {
            logger.info("Deleting confluent schema {}", confluentSubject);
            try {
                confluentService.deleteSubject(confluentSubject);
            } catch (RestClientException e) {
                if (e.getStatus() == 404) {
                    //subjects may be already deleted
                    continue;
                }
                throw e;
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
