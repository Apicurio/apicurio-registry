package io.apicurio.registry.limits;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

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
        clientV3
            .groups()
            // TODO: verify groupId = null cannot be used
            .byGroupId("default")
            .artifacts()
            .byArtifactId(artifactId)
            .versions()
            .byVersion("1")
            .meta()
            .put(meta)
            ;

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
        var exception1 = Assertions.assertThrows(ApiException.class, () -> {
            clientV3
                .groups()
                .byGroupId("default")
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersion("1")
                .meta()
                .put(invalidmeta)
                ;
        });
        Assertions.assertEquals(409, exception1.getResponseStatusCode());

        //schema number 3 , exceeds the max number of schemas
        var exception2 = Assertions.assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            ArtifactContent data = new ArtifactContent();
            data.setContent("{}");
            clientV3
                .groups()
                .byGroupId("default")
                .artifacts()
                .post(data, config -> {
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                });
        });
        Assertions.assertEquals(409, exception2.getErrorCode());
    }

}