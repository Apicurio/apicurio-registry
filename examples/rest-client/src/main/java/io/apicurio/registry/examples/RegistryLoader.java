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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryLoader {

    public static void main(String[] args) throws Exception {
        String registryUrl = "http://localhost:8080/apis/registry/v2";
        RegistryClient client = RegistryClientFactory.create(registryUrl, Collections.emptyMap());

        File templateFile = new File("C:\\Temp\\registry.json");
        String template;
        try (InputStream templateIS = new FileInputStream(templateFile)) {
            template = IoUtil.toString(templateIS);
        }

        for (int idx = 1; idx <= 1000; idx++) {
            System.out.println("Creating artifact #" + idx);
            String content = template.replaceFirst("Apicurio Registry API", "Apicurio Registry API :: Copy #" + idx);
            InputStream contentIS = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            client.createArtifact(null, null, contentIS);
        }
    }

}
