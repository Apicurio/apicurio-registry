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

package io.apicurio.registry.limits;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;


@QuarkusTest
@TestProfile(LimitsTestProfile.class)
@DisabledIfEnvironmentVariable(named = AbstractRegistryTestBase.CURRENT_ENV, matches = AbstractRegistryTestBase.CURRENT_ENV_MAS_REGEX)
@Tag(ApicurioTestTags.SLOW)
public class LimitsTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeAll
    public void cleanUpData() {
        storage.deleteAllUserData();
    }

    @Test
    public void testLimits() throws Exception {

        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);
        String content = IoUtil.toString(jsonSchema);

        String artifactId = TestUtils.generateArtifactId();

        createArtifact(artifactId, ArtifactType.JSON, content);
        createArtifactVersion(artifactId, ArtifactType.JSON, content);

        //valid metadata
        EditableMetaData meta = new EditableMetaData();
        meta.setName(StringUtils.repeat('a', 512));
        meta.setDescription(StringUtils.repeat('a', 1024));
        String fourBytesText = StringUtils.repeat('a', 4);
        var props = new io.apicurio.registry.rest.client.models.Properties();
        props.setAdditionalData(Map.of(
                StringUtils.repeat('a', 4), fourBytesText,
                StringUtils.repeat('b', 4), fourBytesText));
        meta.setProperties(props);
        meta.setLabels(Arrays.asList(fourBytesText, fourBytesText));
        clientV2
            .groups()
            // TODO: verify groupId = null cannot be used
            .byGroupId("default")
            .artifacts()
            .byArtifactId(artifactId)
            .versions()
            .byVersion("1")
            .meta()
            .put(meta)
            .get(3, TimeUnit.SECONDS);

        //invalid metadata
        EditableMetaData invalidmeta = new EditableMetaData();
        invalidmeta.setName(StringUtils.repeat('a', 513));
        invalidmeta.setDescription(StringUtils.repeat('a', 1025));
        String fiveBytesText = StringUtils.repeat('a', 5);
        var props2 = new io.apicurio.registry.rest.client.models.Properties();
        props2.setAdditionalData(Map.of(
                StringUtils.repeat('a', 5), fiveBytesText,
                StringUtils.repeat('b', 5), fiveBytesText));
        invalidmeta.setProperties(props2);
        invalidmeta.setLabels(Arrays.asList(fiveBytesText, fiveBytesText));
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            clientV2
                .groups()
                .byGroupId("default")
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersion("1")
                .meta()
                .put(invalidmeta)
                .get(3, TimeUnit.SECONDS);
        });
        Assertions.assertNotNull(executionException1.getCause());
        Assertions.assertEquals(ApiException.class, executionException1.getCause().getClass());
        Assertions.assertEquals(409, ((ApiException)executionException1.getCause()).getResponseStatusCode());

        //schema number 3 , exceeds the max number of schemas
        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
            ArtifactContent data = new ArtifactContent();
            data.setContent("{}");
            clientV2
                .groups()
                .byGroupId("default")
                .artifacts()
                .post(data, config -> {
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                }).get(3, TimeUnit.SECONDS);
        });
        Assertions.assertNotNull(executionException2.getCause());
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class, executionException2.getCause().getClass());
        Assertions.assertEquals(409, ((io.apicurio.registry.rest.client.models.Error)executionException2.getCause()).getErrorCode());
    }

}