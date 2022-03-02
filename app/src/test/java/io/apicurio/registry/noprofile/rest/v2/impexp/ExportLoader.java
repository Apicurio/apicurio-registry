/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.noprofile.rest.v2.impexp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.AdminClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rbac.AdminResourceTest;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RuleType;

/**
 * Used to create the export.zip file used by the import test in {@link AdminResourceTest}.
 * @author eric.wittmann@gmail.com
 */
public class ExportLoader {

    private static final String CONTENT = "{\r\n" +
            "    \"openapi\": \"3.0.2\",\r\n" +
            "    \"info\": {\r\n" +
            "        \"title\": \"Empty API\",\r\n" +
            "        \"version\": \"1.0.0\",\r\n" +
            "        \"description\": \"An example API design using OpenAPI.\"\r\n" +
            "    }\r\n" +
            "}";

    public static void main(String[] args) throws IOException {
        RegistryClient client = RegistryClientFactory.create("http://localhost:8080/apis/registry/v2");
        AdminClient adminClient = AdminClientFactory.create("http://localhost:8080/apis/registry/v2");
        for (int idx = 0; idx < 1000; idx++) {
            System.out.println("Iteration: " + idx);
            try (ByteArrayInputStream data = new ByteArrayInputStream(CONTENT.replace("1.0.0", "1.0." + idx).getBytes())) {
                String artifactId = UUID.randomUUID().toString();
                client.createArtifact("default", artifactId, data);
                client.deleteArtifact("default", artifactId);
            }
        }

        String testContent = CONTENT.replace("Empty API", "Test Artifact");

        ByteArrayInputStream data = new ByteArrayInputStream(testContent.replace("1.0.0", "1.0.1").getBytes());
        client.createArtifact("ImportTest", "Artifact-1", "1.0.1", data);
        data = new ByteArrayInputStream(testContent.replace("1.0.0", "1.0.2").getBytes());
        client.createArtifactVersion("ImportTest", "Artifact-1", "1.0.2", data);
        data = new ByteArrayInputStream(testContent.replace("1.0.0", "1.0.3").getBytes());
        client.createArtifactVersion("ImportTest", "Artifact-1", "1.0.3", data);

        data = new ByteArrayInputStream(testContent.replace("1.0.0", "1.0.1").getBytes());
        client.createArtifact("ImportTest", "Artifact-2", "1.0.1", data);

        data = new ByteArrayInputStream(testContent.replace("1.0.0", "1.0.2").getBytes());
        client.createArtifact("ImportTest", "Artifact-3", "1.0.2", data);

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        client.createArtifactRule("ImportTest", "Artifact-1", rule);

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        adminClient.createGlobalRule(rule);
    }

}
