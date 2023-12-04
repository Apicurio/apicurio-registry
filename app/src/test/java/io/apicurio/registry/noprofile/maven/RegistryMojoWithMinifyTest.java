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

package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;


@QuarkusTest
public class RegistryMojoWithMinifyTest extends RegistryMojoTestBase {
    RegisterRegistryMojo registerMojo;

    @BeforeEach
    public void createMojo() {
        this.registerMojo = new RegisterRegistryMojo();
        this.registerMojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @Test
    public void testMinify() throws Exception {
        String groupId = "RegisterWithMinifyRegistryMojoTest";

        File avroFile = new File(getClass().getResource("avro.avsc").getFile());

        RegisterArtifact avroMinifiedArtifact = new RegisterArtifact();
        avroMinifiedArtifact.setGroupId(groupId);
        avroMinifiedArtifact.setArtifactId("userInfoMinified");
        avroMinifiedArtifact.setType(ArtifactType.AVRO);
        avroMinifiedArtifact.setMinify(true);
        avroMinifiedArtifact.setFile(avroFile);

        RegisterArtifact avroNotMinifiedArtifact = new RegisterArtifact();
        avroNotMinifiedArtifact.setGroupId(groupId);
        avroNotMinifiedArtifact.setArtifactId("userInfoNotMinified");
        avroNotMinifiedArtifact.setType(ArtifactType.AVRO);
        avroNotMinifiedArtifact.setFile(avroFile);

        registerMojo.setArtifacts(List.of(avroMinifiedArtifact, avroNotMinifiedArtifact));
        registerMojo.execute();

        // Wait for the artifact to be created.
        TestUtils.retry(() -> {
            InputStream artifactInputStream = clientV2.groups().byGroupId(groupId).artifacts().byArtifactId("userInfoMinified").get().get(3, TimeUnit.SECONDS);
            String artifactContent = new String(artifactInputStream.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertEquals("{\"type\":\"record\",\"name\":\"userInfo\",\"namespace\":\"my.example\",\"fields\":[{\"name\":\"age\",\"type\":\"int\"}]}", artifactContent);
        });

        // Wait for the artifact to be created.
        TestUtils.retry(() -> {
            InputStream artifactInputStream = clientV2.groups().byGroupId(groupId).artifacts().byArtifactId("userInfoNotMinified").get().get(3, TimeUnit.SECONDS);
            String artifactContent = new String(artifactInputStream.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertEquals("{\n" +
                    "  \"type\" : \"record\",\n" +
                    "  \"name\" : \"userInfo\",\n" +
                    "  \"namespace\" : \"my.example\",\n" +
                    "  \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\n" +
                    "}", artifactContent);
        });
    }

}
