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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.apicurio.registry.maven.DownloadArtifact;
import io.apicurio.registry.maven.DownloadRegistryMojo;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class DownloadRegistryMojoTest extends RegistryMojoTestBase {
    DownloadRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new DownloadRegistryMojo();
        this.mojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @Test
    public void testDownloadIds() throws Exception {
        String groupId = DownloadRegistryMojoTest.class.getName();
        String artifactId = generateArtifactId();

        Schema schema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)));
        ArtifactContent data = new ArtifactContent();
        data.setContent(schema.toString());
        clientV2.groups().byGroupId(groupId).artifacts().post(data, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
        }).get(3, TimeUnit.SECONDS);

        List<DownloadArtifact> artifacts = new ArrayList<>();
        DownloadArtifact artifact = new DownloadArtifact();
        artifact.setGroupId(groupId);
        artifact.setArtifactId(artifactId);
        artifact.setOverwrite(true);
        artifact.setFile(new File(this.tempDirectory, "avro-schema.avsc"));
        artifacts.add(artifact);

        Assertions.assertFalse(artifact.getFile().isFile());

        mojo.setArtifacts(artifacts);
        mojo.execute();

        Assertions.assertTrue(artifact.getFile().isFile());
    }

}
