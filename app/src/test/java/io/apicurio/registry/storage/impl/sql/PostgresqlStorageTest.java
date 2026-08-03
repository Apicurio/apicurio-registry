package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(PostgresqlTestProfile.class)
@Typed(PostgresqlStorageTest.class)
public class PostgresqlStorageTest extends DefaultRegistryStorageTest {

    /**
     * Postgres-only test that exercises the outbox INSERT with the true maximum aggregate id length
     * (512 + 1 + 512 + 1 + 256 = 1282 chars). This cannot run on MSSQL due to its 900-byte clustered
     * index key limit on composite PKs involving these identifier columns.
     */
    @Test
    public void testCreateArtifactWithTrueMaxLengthIdentifiers() throws Exception {
        String maxGroupId = "g".repeat(512);
        String maxArtifactId = "a".repeat(512);
        String maxVersion = "v".repeat(256);
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);

        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(maxGroupId, maxArtifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        EditableVersionMetaDataDto.builder().build(), Collections.emptyList(), false, false,
                        null)
                .getValue();

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(maxGroupId, dto.getGroupId());
        Assertions.assertEquals(maxArtifactId, dto.getArtifactId());

        ContentHandle contentV2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactVersionMetaDataDto v2 = storage().createArtifactVersion(maxGroupId, maxArtifactId,
                maxVersion, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentV2).build(),
                null, Collections.emptyList(), false, false, null);

        Assertions.assertNotNull(v2);
        Assertions.assertEquals(maxGroupId, v2.getGroupId());
        Assertions.assertEquals(maxArtifactId, v2.getArtifactId());
        Assertions.assertEquals(maxVersion, v2.getVersion());

        int aggregateIdLength = v2.getGroupId().length() + 1 + v2.getArtifactId().length() + 1
                + v2.getVersion().length();
        Assertions.assertEquals(1282, aggregateIdLength,
                "Aggregate id must reach the true maximum of 1282 chars");

        StoredArtifactVersionDto storedVersion = storage().getArtifactVersionContent(maxGroupId,
                maxArtifactId, maxVersion);
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT_V2, storedVersion.getContent().content());
    }
}
