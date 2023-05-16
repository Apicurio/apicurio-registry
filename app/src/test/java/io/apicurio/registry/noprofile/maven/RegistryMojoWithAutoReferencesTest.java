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
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@QuarkusTest
public class RegistryMojoWithAutoReferencesTest extends RegistryMojoTestBase {

    private static final String PROTO_SCHEMA_EXTENSION = ".proto";
    private static final String AVSC_SCHEMA_EXTENSION = ".avsc";
    private static final String JSON_SCHEMA_EXTENSION = ".json";


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
        String groupId = "autoRegisterAvroWithReferences";
        String artifactId = "tradeRaw";

        File tradeRawFile = new File(getClass().getResource("TradeRawArray.avsc").getFile());

        Set<String> avroFiles = Arrays.stream(Objects.requireNonNull(tradeRawFile.getParentFile().listFiles((dir, name) -> name.endsWith(AVSC_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis);
                })
                .collect(Collectors.toSet());

        RegisterArtifact tradeRawArtifact = new RegisterArtifact();
        tradeRawArtifact.setGroupId(groupId);
        tradeRawArtifact.setArtifactId(artifactId);
        tradeRawArtifact.setType(ArtifactType.AVRO);
        tradeRawArtifact.setFile(tradeRawFile);
        tradeRawArtifact.setAnalyzeDirectory(true);
        tradeRawArtifact.setIfExists(IfExists.FAIL);

        registerMojo.setArtifacts(Collections.singletonList(tradeRawArtifact));
        registerMojo.execute();

        //Assertions
        validateStructure(groupId, artifactId, 1, 3, avroFiles);
    }

    @Test
    public void autoRegisterProtoWithReferences() throws MojoExecutionException, MojoFailureException {
        //Preparation
        String groupId = "autoRegisterProtoWithReferences";
        String artifactId = "tableNotification";

        File tableNotificationFile = new File(getClass().getResource("table_notification.proto").getFile());

        Set<String> protoFiles = Arrays.stream(Objects.requireNonNull(tableNotificationFile.getParentFile().listFiles((dir, name) -> name.endsWith(PROTO_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis).trim();
                })
                .collect(Collectors.toSet());

        RegisterArtifact tableNotification = new RegisterArtifact();
        tableNotification.setGroupId(groupId);
        tableNotification.setArtifactId(artifactId);
        tableNotification.setType(ArtifactType.PROTOBUF);
        tableNotification.setFile(tableNotificationFile);
        tableNotification.setAnalyzeDirectory(true);
        tableNotification.setIfExists(IfExists.FAIL);

        registerMojo.setArtifacts(Collections.singletonList(tableNotification));

        //Execution
        registerMojo.execute();

        //Assertions
        validateStructure(groupId, artifactId, 2, 4, protoFiles);
    }

    @Test
    public void autoRegisterJsonSchemaWithReferences() throws MojoExecutionException, MojoFailureException {
        //Preparation
        String groupId = "autoRegisterJsonSchemaWithReferences";
        String artifactId = "citizen";

        File citizenFile = new File(getClass().getResource("citizen.json").getFile());

        Set<String> protoFiles = Arrays.stream(Objects.requireNonNull(citizenFile.getParentFile().listFiles((dir, name) -> name.endsWith(JSON_SCHEMA_EXTENSION))))
                .map(file -> {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                    }
                    return IoUtil.toString(fis).trim();
                })
                .collect(Collectors.toSet());

        RegisterArtifact citizen = new RegisterArtifact();
        citizen.setGroupId(groupId);
        citizen.setArtifactId(artifactId);
        citizen.setType(ArtifactType.JSON);
        citizen.setFile(citizenFile);
        citizen.setAnalyzeDirectory(true);
        citizen.setIfExists(IfExists.FAIL);

        registerMojo.setArtifacts(Collections.singletonList(citizen));

        //Execution
        registerMojo.execute();

        //Assertions
        validateStructure(groupId, artifactId, 3, 4, protoFiles);
    }

    private void validateStructure(String groupId, String artifactId, int expectedMainReferences, int expectedTotalArtifacts, Set<String> originalContents) {
        final ArtifactMetaData artifactWithReferences = clientV2.getArtifactMetaData(groupId, artifactId);
        final String mainContent = IoUtil.toString(clientV2.getArtifactVersion(groupId, artifactId, artifactWithReferences.getVersion()));

        Assertions.assertTrue(originalContents.contains(mainContent)); //The main content has been registered as-is.

        final List<ArtifactReference> mainArtifactReferences = clientV2.getArtifactReferencesByGlobalId(artifactWithReferences.getGlobalId());

        //The main artifact has the expected number of references
        Assertions.assertEquals(expectedMainReferences, clientV2.getArtifactReferencesByGlobalId(artifactWithReferences.getGlobalId()).size());

        //Validate all the contents are registered as they are in the file system.
        validateReferences(mainArtifactReferences, originalContents);

        //The total number of artifacts for the directory structure is the expected.
        Assertions.assertEquals(expectedTotalArtifacts, clientV2.listArtifactsInGroup(groupId).getCount());
    }

    private void validateReferences(List<ArtifactReference> artifactReferences, Set<String> loadedContents) {
        for (ArtifactReference artifactReference : artifactReferences) {
            String referenceContent = IoUtil.toString(clientV2.getArtifactVersion(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion()));
            ArtifactMetaData referenceMetadata = clientV2.getArtifactMetaData(artifactReference.getGroupId(), artifactReference.getArtifactId());
            Assertions.assertTrue(loadedContents.contains(referenceContent.trim()));

            List<ArtifactReference> nestedReferences = clientV2.getArtifactReferencesByGlobalId(referenceMetadata.getGlobalId());

            if (!nestedReferences.isEmpty()) {
                validateReferences(nestedReferences, loadedContents);
            }
        }
    }
}