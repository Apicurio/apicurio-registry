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
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MetadataConfluentIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataConfluentIT.class);

    @Test
    void getAndUpdateMetadataOfSchema() throws IOException, RestClientException {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}");
        String schemaSubject = "schema-example";

        int schemaId = confluentService.register(schemaSubject, schema);

        schema = confluentService.getById(schemaId);
        SchemaMetadata schemaMetadata = confluentService.getSchemaMetadata(schemaSubject, schemaId);

        LOGGER.info("Scheme name:{} has following metadata:{}", schema.getFullName(), schemaMetadata.getSchema());

        assertThat(schemaMetadata.getId(), is(1));
        assertThat(schemaMetadata.getVersion(), is(1));
        assertThat("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}", is(schemaMetadata.getSchema()));
        // IMPORTANT NOTE: we can not test schema metadata, because they are mapping on the same endpoint when we are creating the schema...
    }
}
