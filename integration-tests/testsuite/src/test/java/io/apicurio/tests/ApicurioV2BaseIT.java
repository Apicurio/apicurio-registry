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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.rest.v2.beans.SearchedVersion;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.utils.RegistryUtils;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

/**
 * @author Fabian Martinez
 */
public class ApicurioV2BaseIT extends ApicurioRegistryBaseIT {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Function<Exception, Integer> errorCodeExtractor = e -> ((RestClientException) e).getError().getErrorCode();

    protected final RegistryClient registryClient = createRegistryClient();

    protected RegistryClient createRegistryClient() {
        if (!TestUtils.isExternalRegistry() && RegistryUtils.TEST_PROFILE.contains(Constants.CLUSTERED)) {

            int c2port = TestUtils.getRegistryPort() + 1;

            return new LoadBalanceRegistryClient(Arrays.asList("http://localhost:" + TestUtils.getRegistryPort(), "http://localhost:" + c2port));
        } else {
            return RegistryClientFactory.create(TestUtils.getRegistryBaseUrl());
        }
    }

    @BeforeAll
    void prepareRestAssured() {
        RestAssured.baseURI = TestUtils.getRegistryV2ApiUrl();
        logger.info("RestAssured configured with {}", RestAssured.baseURI);
        RestAssured.defaultParser = Parser.JSON;
        RestAssured.urlEncodingEnabled = false;
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
        TestUtils.retry(() -> assertTrue(registryClient.searchArtifacts(null, null, null, null, null, null, null, null, null).getCount() == 0));
    }

    protected ArtifactMetaData createArtifact(String groupId, String artifactId, ArtifactType artifactType, InputStream artifact) throws Exception {
        ArtifactMetaData amd = registryClient.createArtifact(groupId, artifactId, null, artifactType, IfExists.FAIL, false, artifact);

        // make sure we have schema registered
        retry(() -> registryClient.getContentByGlobalId(amd.getGlobalId()));
        retry(() -> registryClient.getArtifactVersionMetaData(amd.getGroupId(), amd.getId(), String.valueOf(amd.getVersion())));
        return amd;
    }

    protected VersionMetaData createArtifactVersion(String groupId, String artifactId, InputStream artifact) throws Exception {
        VersionMetaData meta = registryClient.createArtifactVersion(groupId, artifactId, null, artifact);

        //wait for storage
        retry(() -> registryClient.getContentByGlobalId(meta.getGlobalId()));
        retry(() -> registryClient.getArtifactVersionMetaData(meta.getGroupId(), meta.getId(), String.valueOf(meta.getVersion())));
        return meta;
    }

    protected ArtifactMetaData updateArtifact(String groupId, String artifactId, InputStream artifact) throws Exception {
        ArtifactMetaData meta = registryClient.updateArtifact(groupId, artifactId, artifact);

        //wait for storage
        retry(() -> registryClient.getContentByGlobalId(meta.getGlobalId()));
        retry(() -> registryClient.getArtifactVersionMetaData(meta.getGroupId(), meta.getId(), String.valueOf(meta.getVersion())));
        return meta;
    }

    protected List<String> listArtifactVersions(String groupId, String artifactId) {
        return registryClient.listArtifactVersions(groupId, artifactId, 0, 10)
                .getVersions()
                .stream()
                .map(SearchedVersion::getVersion)
                .collect(Collectors.toList());
    }

    protected final String resourceToString(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
