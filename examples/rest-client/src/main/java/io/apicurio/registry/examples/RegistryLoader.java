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
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.kiota.http.vertx.VertXRequestAdapter;

import java.util.UUID;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryLoader {

    public static void main(String[] args) throws Exception {
        String registryUrl = "http://localhost:8080/apis/registry/v2";

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
            Task task = new Task(simpleAvro, client, 1000, i + 800000);
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
                ArtifactContent content = new ArtifactContent();
                content.setContent(simpleAvro.replace("userInfo", "userInfo" + threadId + numArtifacts));
                client.groups().byGroupId("default").artifacts().post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                });
                Rule rule = new Rule();
                rule.setType(RuleType.VALIDITY);
                rule.setConfig("SYNTAX_ONLY");
                client.groups().byGroupId("default").artifacts().byArtifactId(artifactId).rules().post(rule);
            }
        }
    }
}
