package io.apicurio.tests.smokeTests.confluent;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ConfluentBaseIT;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static io.apicurio.tests.utils.Constants.SMOKE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Tag(SMOKE)
@QuarkusIntegrationTest
public class MetadataConfluentIT extends ConfluentBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataConfluentIT.class);

    @Test
    @Tag(ACCEPTANCE)
    void getAndUpdateMetadataOfSchema() throws IOException, RestClientException, TimeoutException {
        ParsedSchema schema = new AvroSchema(
                "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        String schemaSubject = TestUtils.generateArtifactId();

        int schemaId = createArtifactViaConfluentClient(schema, schemaSubject);

        schema = confluentService.getSchemaById(schemaId);
        SchemaMetadata schemaMetadata = confluentService.getSchemaMetadata(schemaSubject, 1);

        LOGGER.info("Scheme name: {} has following metadata: {}", schema.name(), schemaMetadata.getSchema());

        assertThat(schemaMetadata.getId(), is(schemaId));
        assertThat(schemaMetadata.getVersion(), is(1));
        assertThat(
                "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}",
                is(schemaMetadata.getSchema()));
        // IMPORTANT NOTE: we can not test schema metadata, because they are mapping on the same endpoint when
        // we are creating the schema...
    }
}
