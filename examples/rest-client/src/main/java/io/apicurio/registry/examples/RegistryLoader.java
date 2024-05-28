/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.examples;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.kiota.http.vertx.VertXRequestAdapter;

import java.util.UUID;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryLoader {

    public static void main(String[] args) throws Exception {
        String registryUrl = "http://localhost:8080/apis/registry/v3";

        VertXRequestAdapter vertXRequestAdapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        vertXRequestAdapter.setBaseUrl(registryUrl);
        RegistryClient client = new RegistryClient(vertXRequestAdapter);

        String simpleAvro = """
                {
                    "type" : "record",
                    "name" : "userInfo",
                    "namespace" : "my.example",
                    "fields" : [{"name" : "age", "type" : "int"}]
                }""";

        for (int i = 0; i < 600; i++) {
            Task task = new Task(simpleAvro, client, 100, i + 100);
            task.start();
        }
    }

    protected static class Task extends Thread {

        private final RegistryClient client;
        private final String simpleAvro;
        private final int numArtifacts;
        private final int threadId;

        public Task(String artifactContent, RegistryClient client, int numArtifacts, int threadId) {
            this.client = client;
            this.simpleAvro = artifactContent;
            this.numArtifacts = numArtifacts;
            this.threadId = threadId;
        }

        @Override
        public void run() {
            for (int idx = 0; idx < numArtifacts; idx++) {
                System.out.println("Iteration: " + idx);
                String artifactId = UUID.randomUUID().toString();

                CreateArtifact createArtifact = new CreateArtifact();
                createArtifact.setType("AVRO");
                createArtifact.setArtifactId(artifactId);
                CreateVersion createVersion = new CreateVersion();
                createArtifact.setFirstVersion(createVersion);
                VersionContent versionContent = new VersionContent();
                createVersion.setContent(versionContent);
                versionContent.setContent(simpleAvro.replace("userInfo", "userInfo" + threadId + numArtifacts));
                versionContent.setContentType("application/json");

                client.groups().byGroupId("default").artifacts().post(createArtifact, config -> {
                });

                Rule rule = new Rule();
                rule.setType(RuleType.VALIDITY);
                rule.setConfig("SYNTAX_ONLY");
                client.groups().byGroupId("default").artifacts().byArtifactId(artifactId).rules().post(rule);
            }
        }
    }
}
