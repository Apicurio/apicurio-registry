package io.apicurio.registry.limits;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.InputStream;
import java.util.Map;

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

        createArtifact(artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(artifactId, content, ContentTypes.APPLICATION_JSON);

        //valid metadata
        EditableVersionMetaData meta = new EditableVersionMetaData();
        meta.setName(StringUtils.repeat('a', 512));
        meta.setDescription(StringUtils.repeat('a', 1024));
        String fourBytesText = StringUtils.repeat('a', 4);
        var labels = new Labels();
        labels.setAdditionalData(Map.of(
                StringUtils.repeat('a', 4), fourBytesText,
                StringUtils.repeat('b', 4), fourBytesText));
        meta.setLabels(labels);
        clientV3
            .groups()
            // TODO: verify groupId = null cannot be used
            .byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
            .artifacts()
            .byArtifactId(artifactId)
            .versions()
            .byVersionExpression("1")
            .put(meta)
            ;

        //invalid metadata
        EditableVersionMetaData invalidmeta = new EditableVersionMetaData();
        invalidmeta.setName(StringUtils.repeat('a', 513));
        invalidmeta.setDescription(StringUtils.repeat('a', 1025));
        String fiveBytesText = StringUtils.repeat('a', 5);
        var labels2 = new Labels();
        labels2.setAdditionalData(Map.of(
                StringUtils.repeat('a', 5), fiveBytesText,
                StringUtils.repeat('b', 5), fiveBytesText));
        invalidmeta.setLabels(labels2);
        var exception1 = Assertions.assertThrows(ApiException.class, () -> {
            clientV3
                .groups()
                .byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersionExpression("1")
                .put(invalidmeta)
                ;
        });
        Assertions.assertEquals(409, exception1.getResponseStatusCode());

        //schema number 3 , exceeds the max number of schemas
        var exception2 = Assertions.assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId(artifactId);
            createArtifact.setType(ArtifactType.JSON);
            CreateVersion createVersion = new CreateVersion();
            createArtifact.setFirstVersion(createVersion);
            VersionContent versionContent = new VersionContent();
            createVersion.setContent(versionContent);
            versionContent.setContent("{}");
            versionContent.setContentType(ContentTypes.APPLICATION_JSON);

            clientV3
                .groups()
                .byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
                .artifacts()
                .post(createArtifact);
        });
        Assertions.assertEquals(409, exception2.getErrorCode());
    }

}