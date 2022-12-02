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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.exception.LimitExceededException;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(LimitsTestProfile.class)
@DisabledIfEnvironmentVariable(named = AbstractRegistryTestBase.CURRENT_ENV, matches = AbstractRegistryTestBase.CURRENT_ENV_MAS_REGEX)
@Tag(ApicurioTestTags.SLOW)
public class LimitsTest extends AbstractResourceTestBase {

    @Test
    public void testLimitsNoMultitenancy() throws Exception {

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
        meta.setProperties(Map.of(
                StringUtils.repeat('a', 4), fourBytesText,
                StringUtils.repeat('b', 4), fourBytesText));
        meta.setLabels(Arrays.asList(fourBytesText, fourBytesText));
        clientV2.updateArtifactVersionMetaData(null, artifactId, "1", meta);

        //invalid metadata
        EditableMetaData invalidmeta = new EditableMetaData();
        invalidmeta.setName(StringUtils.repeat('a', 513));
        invalidmeta.setDescription(StringUtils.repeat('a', 1025));
        String fiveBytesText = StringUtils.repeat('a', 5);
        invalidmeta.setProperties(Map.of(
                StringUtils.repeat('a', 5), fiveBytesText,
                StringUtils.repeat('b', 5), fiveBytesText));
        invalidmeta.setLabels(Arrays.asList(fiveBytesText, fiveBytesText));
        Assertions.assertThrows(LimitExceededException.class, () -> {
            clientV2.updateArtifactVersionMetaData(null, artifactId, "1", invalidmeta);
        });

        //schema number 3 , exceeds the max number of schemas
        Assertions.assertThrows(LimitExceededException.class, () -> {
            clientV2.createArtifact(null, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });

    }

}