/*
 * Copyright 2023 Red Hat
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

import io.apicurio.registry.maven.DownloadRegistryMojo;
import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;

@QuarkusTest
public class RegistryMojoWithAutoReferencesTest extends RegistryMojoTestBase {

    RegisterRegistryMojo registerMojo;
    DownloadRegistryMojo downloadMojo;

    @BeforeEach
    public void createMojos() {
        this.registerMojo = new RegisterRegistryMojo();
        this.registerMojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));

        this.downloadMojo = new DownloadRegistryMojo();
        this.downloadMojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @Test
    public void autoRegisterAvroWithReferences() throws MojoExecutionException, MojoFailureException {
        String groupId = "RegistryMojoWithAutoReferencesTest";

        File tradeRawFile = new File(getClass().getResource("TradeRawArray.avsc").getFile());

        RegisterArtifact tradeRawArtifact = new RegisterArtifact();
        tradeRawArtifact.setGroupId(groupId);
        tradeRawArtifact.setArtifactId("tradeRaw");
        tradeRawArtifact.setType(ArtifactType.AVRO);
        tradeRawArtifact.setFile(tradeRawFile);
        tradeRawArtifact.setAnalyzeDirectory(true);

        registerMojo.setArtifacts(Collections.singletonList(tradeRawArtifact));
        registerMojo.execute();
    }

    @Test
    public void autoRegisterProtoWithReferences() throws MojoExecutionException, MojoFailureException {
        String groupId = "autoRegisterProtoWithReferences";

        File tradeRawFile = new File(getClass().getResource("table_notification.proto").getFile());

        RegisterArtifact tableNotification = new RegisterArtifact();
        tableNotification.setGroupId(groupId);
        tableNotification.setArtifactId("tableNotification");
        tableNotification.setType(ArtifactType.PROTOBUF);
        tableNotification.setFile(tradeRawFile);
        tableNotification.setAnalyzeDirectory(true);

        registerMojo.setArtifacts(Collections.singletonList(tableNotification));
        registerMojo.execute();
    }
}
