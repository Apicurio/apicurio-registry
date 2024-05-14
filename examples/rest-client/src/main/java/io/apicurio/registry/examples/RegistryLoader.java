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
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.rest.client.util.IoUtil;
import io.kiota.http.vertx.VertXRequestAdapter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryLoader {

    public static void main(String[] args) throws Exception {
        String registryUrl = "http://localhost:8080/apis/registry/v2";

        VertXRequestAdapter vertXRequestAdapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        vertXRequestAdapter.setBaseUrl(registryUrl);
        RegistryClient client = new RegistryClient(vertXRequestAdapter);

        File templateFile = new File("C:\\Temp\\registry.json");
        String template;
        try (InputStream templateIS = new FileInputStream(templateFile)) {
            template = IoUtil.toString(templateIS);
        }

        for (int idx = 1; idx <= 1000; idx++) {
            System.out.println("Creating artifact #" + idx);
            String content = template.replaceFirst("Apicurio Registry API", "Apicurio Registry API :: Copy #" + idx);
            InputStream contentIS = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId("city");
            createArtifact.setType("JSON");
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(IoUtil.toString(contentIS));
            createArtifact.getFirstVersion().getContent().setContentType("application/json");

            client.groups().byGroupId("default").artifacts().post(createArtifact, config -> {
                config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
            });
        }
    }

}
