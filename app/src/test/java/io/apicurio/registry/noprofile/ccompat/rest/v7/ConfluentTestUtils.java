/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.content.ContentHandle;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class ConfluentTestUtils {

    private static final Random random = new Random();


    /**
     * Helper method which checks the number of versions registered under the given subject.
     */
    public static void checkNumberOfVersions(RestService restService, int expected, String subject) throws IOException, RestClientException {
        List<Integer> versions = restService.getAllVersions(subject);
        assertEquals("Expected " + expected + " registered versions under subject " + subject + ", but found " + versions.size(), expected, versions.size());
    }

    /**
     * Register a new schema and verify that it can be found on the expected version.
     */
    public static int registerAndVerifySchema(RestService restService, String schemaString, String subject) throws IOException, RestClientException {
        int registeredId = restService.registerSchema(schemaString, subject);

        // the newly registered schema should be immediately readable on the leader
        assertEquals("Registered schema should be found", schemaString, restService.getId(registeredId).getSchemaString());

        return registeredId;
    }

    public static void registerAndVerifySchema(RestService restService, String schemaString, List<SchemaReference> references, String subject) throws IOException, RestClientException {
        int registeredId = restService.registerSchema(schemaString, AvroSchema.TYPE, references, subject);

        // the newly registered schema should be immediately readable on the leader
        assertEquals("Registered schema should be found", schemaString, restService.getId(registeredId).getSchemaString());
    }

    public static List<String> getRandomCanonicalAvroString(int num) {
        List<String> avroStrings = new ArrayList<String>();

        for (int i = 0; i < num; i++) {
            String schemaString = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + random.nextInt(Integer.MAX_VALUE) + "\"}]}";
            avroStrings.add(new AvroSchema(ContentHandle.create(schemaString).content()).canonicalString());
        }
        return avroStrings;
    }

    public static List<String> getAvroSchemaWithReferences() {
        List<String> schemas = new ArrayList<>();
        String reference = "{\"type\":\"record\"," + "\"name\":\"Subrecord\"," + "\"namespace\":\"otherns\"," + "\"fields\":" + "[{\"name\":\"field2\",\"type\":\"string\"}]}";
        schemas.add(reference);
        String schemaString = "{\"type\":\"record\"," + "\"name\":\"MyRecord\"," + "\"namespace\":\"ns\"," + "\"fields\":" + "[{\"name\":\"field1\",\"type\":\"otherns.Subrecord\"}]}";
        schemas.add(schemaString);
        return schemas;
    }


    public static String getBadSchema() {
        return "{\"type\":\"bad-record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + random.nextInt(Integer.MAX_VALUE) + "\"}]}";
    }
}
