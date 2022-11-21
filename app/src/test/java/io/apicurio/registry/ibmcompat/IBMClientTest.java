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

package io.apicurio.registry.ibmcompat;

import org.junit.jupiter.api.Assertions;

import io.apicurio.registry.AbstractResourceTestBase;

//@QuarkusTest
// TODO re-enable once I figure out what this is doing.
public class IBMClientTest extends AbstractResourceTestBase {

    private SchemaRegistryRestAPIClient buildClient() throws Exception {
        return new SchemaRegistryRestAPIClient(registryApiBaseUrl + "/ibmcompat/v1", "<API_KEY>", true);
    }

//    @Test
    public void testSmoke() throws Exception {
        SchemaRegistryRestAPIClient client = buildClient();

        String id = generateArtifactId();
        String content = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";

        @SuppressWarnings("rawtypes")
        SchemaRegistryRestAPIClient.Tuple tuple = client.create(id, content, true);
        Assertions.assertNotNull(tuple);
        Assertions.assertEquals(200, tuple.status);
        Assertions.assertEquals(content, tuple.result);

        tuple = client.create(id, content, false);
        Assertions.assertNotNull(tuple);
        Assertions.assertEquals(201, tuple.status);
        Assertions.assertTrue(tuple.result instanceof SchemaRegistryRestAPIClient.SchemaInfoResponse);
        SchemaRegistryRestAPIClient.SchemaInfoResponse sir = (SchemaRegistryRestAPIClient.SchemaInfoResponse) tuple.result;
        String schemaId = sir.id;
        Assertions.assertEquals(id, schemaId);
        Assertions.assertTrue(sir.versions.stream().anyMatch(vr -> vr.id.equals("1")));
        Assertions.assertFalse(sir.versions.stream().anyMatch(vr -> vr.id.equals("2")));
        
        this.waitForArtifact(id);

        SchemaRegistryRestAPIClient.SchemaInfoResponse info = client.get(schemaId);
        Assertions.assertNotNull(info);
        Assertions.assertEquals(schemaId, info.id);
        Assertions.assertNotNull(info.versions);
        Assertions.assertTrue(info.versions.stream().anyMatch(vr -> vr.id.equals("1")));

        SchemaRegistryRestAPIClient.SchemaResponse response = client.get(schemaId, "1");
        Assertions.assertNotNull(response);
        Assertions.assertEquals(schemaId, response.id);
        Assertions.assertEquals(content, response.definition);

        content = "{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"f2\",\"type\":\"string\"}]}";

        tuple = client.update(id, content, true);
        Assertions.assertNotNull(tuple);
        Assertions.assertEquals(200, tuple.status);
        Assertions.assertEquals(content, tuple.result);

        tuple = client.update(id, content, false);
        Assertions.assertNotNull(tuple);
        Assertions.assertEquals(201, tuple.status);
        Assertions.assertTrue(tuple.result instanceof SchemaRegistryRestAPIClient.SchemaInfoResponse);
        sir = (SchemaRegistryRestAPIClient.SchemaInfoResponse) tuple.result;
        schemaId = sir.id;
        Assertions.assertEquals(id, schemaId);
        Assertions.assertTrue(sir.versions.stream().anyMatch(vr -> vr.id.equals("1")));
        Assertions.assertTrue(sir.versions.stream().anyMatch(vr -> vr.id.equals("2")));

        response = client.get(schemaId, "2");
        Assertions.assertNotNull(response);
        Assertions.assertEquals(schemaId, response.id);
        Assertions.assertEquals(content, response.definition);

        info = client.get(schemaId);
        Assertions.assertNotNull(info);
        Assertions.assertEquals(schemaId, info.id);
        Assertions.assertNotNull(info.versions);
        Assertions.assertTrue(info.versions.stream().anyMatch(vr -> vr.id.equals("1")));
        Assertions.assertTrue(info.versions.stream().anyMatch(vr -> vr.id.equals("2")));
    }
}
