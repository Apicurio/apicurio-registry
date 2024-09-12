package io.apicurio.tests.smokeTests.confluent;

import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ConfluentBaseIT;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.tests.utils.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(SMOKE)
@QuarkusIntegrationTest
class RulesResourceConfluentIT extends ConfluentBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataConfluentIT.class);

    private static String wrap(String schema) {
        return "{\"schema\": \"" + schema.replace("\"", "\\\"") + "\"}";
    }

    @Test
    @Tag(ACCEPTANCE)
    void compatibilityGlobalRules() throws Exception {
        var first = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}";
        // Adding a default value to the new field to keep full compatibility
        var second = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}, {\"name\":\"bar\",\"type\":\"string\", \"default\": \"42\"}]}";
        var invalid = "{\"type\": \"bloop\"}";

        ConfluentConfigUtils.createGlobalCompatibilityConfig("FULL");

        ParsedSchema schema = new AvroSchema(first);

        String schemeSubject = TestUtils.generateSubject();
        int schemaId = createArtifactViaConfluentClient(schema, schemeSubject);

        confluentService.getSchemaById(schemaId);

        ParsedSchema newSchema = new AvroSchema(second);

        createArtifactViaConfluentClient(newSchema, schemeSubject);

        LOGGER.info("Checking 'Compability with same scheme' and expected code {}", 200);
        ConfluentConfigUtils.testCompatibility(wrap(second), schemeSubject, 200);

        LOGGER.info("Checking 'Subject not found' and expected code {}", 404);
        ConfluentConfigUtils.testCompatibility(wrap(second), "subject-not-found", 404);

        LOGGER.info("Checking 'Invalid avro format' and expected code {}", 422);
        ConfluentConfigUtils.testCompatibility(wrap(invalid), schemeSubject, 422);

        confluentService.deleteSubject(schemeSubject);
        confluentService.deleteSubject(schemeSubject, true);
        waitForSubjectDeleted(schemeSubject);
    }

    @AfterAll
    void clearRules() throws Exception {
        LOGGER.info("Removing all global rules");
        registryClient.admin().rules().delete();
        retryOp((rc) -> {
            List<RuleType> rules = rc.admin().rules().get();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}
