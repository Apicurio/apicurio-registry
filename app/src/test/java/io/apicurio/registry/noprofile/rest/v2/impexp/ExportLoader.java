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

import java.util.UUID;

import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;

/**
 * Used to create the export.zip file used by the import test in {@link AdminResourceTest}.
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

    public static void main(String[] args) throws Exception {
        var adapter = new OkHttpRequestAdapter(new AnonymousAuthenticationProvider());
        adapter.setBaseUrl("http://localhost:8080/apis/registry/v2");
        RegistryClient client = new RegistryClient(adapter);
        for (int idx = 0; idx < 1000; idx++) {
            System.out.println("Iteration: " + idx);
            String data = CONTENT.replace("1.0.0", "1.0." + idx);
            String artifactId = UUID.randomUUID().toString();
            ArtifactContent content = new ArtifactContent();
            content.setContent(data);
            client.groups().byGroupId("default").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
            }).get();
            client.groups().byGroupId("default").artifacts().byArtifactId(artifactId).delete().get();
        }

        String testContent = CONTENT.replace("Empty API", "Test Artifact");

        ArtifactContent content = new ArtifactContent();
        String data = testContent.replace("1.0.0", "1.0.1");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-1");
            config.headers.add("X-Registry-Version", "1.0.1");
        }).get();
        data = testContent.replace("1.0.0", "1.0.2");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-1");
            config.headers.add("X-Registry-Version", "1.0.2");
        }).get();
        data = testContent.replace("1.0.0", "1.0.3");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-1");
            config.headers.add("X-Registry-Version", "1.0.3");
        }).get();

        data = testContent.replace("1.0.0", "1.0.1");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-2");
            config.headers.add("X-Registry-Version", "1.0.1");
        }).get();

        data = testContent.replace("1.0.0", "1.0.2");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-3");
            config.headers.add("X-Registry-Version", "1.0.2");
        }).get();

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").rules().post(rule).get();

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        client.admin().rules().post(rule);
    }

}
