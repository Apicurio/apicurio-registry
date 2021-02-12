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

package io.apicurio.tests;

import static io.apicurio.registry.utils.tests.TestUtils.retry;

import java.io.InputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.interfaces.TestSeparator;

/**
 * @author Fabian Martinez
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@ExtendWith(RegistryDeploymentManager.class)
public class ApicurioV2BaseIT implements TestSeparator, Constants {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected final RegistryClient registryClient = createRegistryClient();

    protected RegistryClient createRegistryClient() {
        return RegistryClientFactory.create(TestUtils.getRegistryV2ApiUrl());
    }

    @AfterEach
    void cleanArtifacts() throws Exception {
        logger.info("Removing all artifacts");
        ArtifactSearchResults artifacts = registryClient.searchArtifacts(null, null, null, null, null, null, null, null, null);
        for (SearchedArtifact artifact : artifacts.getArtifacts()) {
            try {
                registryClient.deleteArtifact(artifact.getGroupId(), artifact.getId());
            } catch (ArtifactNotFoundException e) {
                //because of async storage artifact may be already deleted but listed anyway
                logger.info(e.getMessage());
            } catch (Exception e) {
                logger.error("", e);
            }
        }
//        List<String> artifacts = registryClient.listArtifactsInGroup(ACCEPTANCE) listArtifacts();
//        for (String artifactId : artifacts) {
//            try {
//                registryClient.deleteArtifact(artifactId);
//            } catch (ArtifactNotFoundException e) {
//                //because of async storage artifact may be already deleted but listed anyway
//                logger.info(e.getMessage());
//            } catch (Exception e) {
//                logger.error("", e);
//            }
//        }
//        TestUtils.retry(() -> assertTrue(registryClient.listArtifacts().isEmpty()));
    }

    protected ArtifactMetaData createArtifact(String groupId, String artifactId, ArtifactType artifactType, InputStream artifact) throws Exception {

        ArtifactMetaData amd = registryClient.createArtifact(groupId, artifactId, null, artifactType, IfExists.FAIL, false, artifact);

        // make sure we have schema registered
        retry(() -> registryClient.getContentByGlobalId(amd.getGlobalId()));
        retry(() -> registryClient.getArtifactVersionMetaData(amd.getGroupId(), amd.getId(), String.valueOf(amd.getVersion())));
        return amd;
    }

}
