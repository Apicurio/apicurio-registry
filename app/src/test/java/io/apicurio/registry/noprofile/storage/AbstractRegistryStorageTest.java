package io.apicurio.registry.noprofile.storage;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.EntityType;
import io.apicurio.registry.utils.tests.TestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractRegistryStorageTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = AbstractRegistryStorageTest.class.getSimpleName();

    protected static final String OPENAPI_CONTENT = "{" + "    \"openapi\": \"3.0.2\"," + "    \"info\": {"
            + "        \"title\": \"Empty API\"," + "        \"version\": \"1.0.0\","
            + "        \"description\": \"An example API design using OpenAPI.\"" + "    }" + "}";
    protected static final String OPENAPI_CONTENT_V2 = "{" + "    \"openapi\": \"3.0.2\"," + "    \"info\": {"
            + "        \"title\": \"Empty API 2\"," + "        \"version\": \"1.0.1\","
            + "        \"description\": \"An example API design using OpenAPI.\"" + "    }" + "}";
    protected static final String OPENAPI_CONTENT_TEMPLATE = "{" + "    \"openapi\": \"3.0.2\","
            + "    \"info\": {" + "        \"title\": \"Empty API 2\"," + "        \"version\": \"VERSION\","
            + "        \"description\": \"An example API design using OpenAPI.\"" + "    }" + "}";

    @Inject
    Logger log;

    /**
     * Gets the artifactStore to use. Subclasses must provide this.
     */
    protected abstract RegistryStorage storage();

    @Test
    public void testGetArtifactIds() throws Exception {

        int size = storage().getArtifactIds(null).size();

        String artifactIdPrefix = "testGetArtifactIds-";
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = artifactIdPrefix + idx;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId,
                    ArtifactType.OPENAPI, null, null, ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                    null, Collections.emptyList(), false, false, null).getValue();
            Assertions.assertNotNull(dto);
            Assertions.assertEquals(GROUP_ID, dto.getGroupId());
            Assertions.assertEquals(artifactId, dto.getArtifactId());

            // Verify group metadata is also created
            GroupMetaDataDto groupMetaDataDto = storage().getGroupMetaData(GROUP_ID);
            Assertions.assertNotNull(groupMetaDataDto);
            Assertions.assertEquals(GROUP_ID, groupMetaDataDto.getGroupId());
        }

        int newsize = storage().getArtifactIds(null).size();
        int newids = newsize - size;
        Assertions.assertEquals(10, newids);
    }

    @Test
    public void testCreateArtifact() throws Exception {
        String artifactId = "testCreateArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto versionMetaDataDto = EditableVersionMetaDataDto.builder().name("Empty API")
                .description("An example API design using OpenAPI.").build();

        EditableArtifactMetaDataDto artifactMetaDataDto = EditableArtifactMetaDataDto.builder()
                .name("Empty API").description("An example API design using OpenAPI.").build();
        ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                artifactMetaDataDto, null, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                versionMetaDataDto, Collections.emptyList(), false, false, null).getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertEquals("1", dto.getVersion());

        StoredArtifactVersionDto storedArtifact = storage().getArtifactVersionContent(GROUP_ID, artifactId,
                dto.getVersion());
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(OPENAPI_CONTENT, storedArtifact.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedArtifact.getVersion());

        ArtifactMetaDataDto amdDto = storage().getArtifactMetaData(GROUP_ID, artifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals("Empty API", amdDto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", amdDto.getDescription());
        Assertions.assertNull(amdDto.getLabels());

        ArtifactVersionMetaDataDto versionMetaDataDto1 = storage().getArtifactVersionMetaData(GROUP_ID,
                artifactId, "1");
        Assertions.assertNotNull(versionMetaDataDto1);
        Assertions.assertEquals(dto.getGlobalId(), versionMetaDataDto1.getGlobalId());
        Assertions.assertEquals("Empty API", versionMetaDataDto1.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", versionMetaDataDto1.getDescription());
        Assertions.assertEquals(VersionState.ENABLED, versionMetaDataDto1.getState());
        Assertions.assertEquals("1", versionMetaDataDto1.getVersion());

        StoredArtifactVersionDto storedVersion = storage().getArtifactVersionContent(dto.getGlobalId());
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT, storedVersion.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedVersion.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedVersion.getVersion());

        storedVersion = storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT, storedVersion.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedVersion.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedVersion.getVersion());

        List<String> versions = storage().getArtifactVersions(GROUP_ID, artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertEquals("1", versions.iterator().next());
    }

    @Test
    public void testCreateArtifactWithMetaData() throws Exception {
        String artifactId = "testCreateArtifactWithMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto metaData = EditableVersionMetaDataDto.builder().name("NAME")
                .description("DESCRIPTION").labels(Collections.singletonMap("KEY", "VALUE")).build();

        EditableArtifactMetaDataDto artifactMetaDataDto = new EditableArtifactMetaDataDto("NAME",
                "DESCRIPTION", null, Collections.singletonMap("KEY", "VALUE"));

        ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                artifactMetaDataDto, null, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                metaData, Collections.emptyList(), false, false, null).getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals("NAME", dto.getName());
        Assertions.assertEquals("DESCRIPTION", dto.getDescription());
        Assertions.assertNotNull(dto.getLabels());
        Assertions.assertEquals(metaData.getLabels(), dto.getLabels());
        Assertions.assertEquals("1", dto.getVersion());

        StoredArtifactVersionDto storedArtifact = storage().getArtifactVersionContent(GROUP_ID, artifactId,
                "1");
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(OPENAPI_CONTENT, storedArtifact.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedArtifact.getVersion());

        ArtifactMetaDataDto amdDto = storage().getArtifactMetaData(GROUP_ID, artifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals("NAME", amdDto.getName());
        Assertions.assertEquals("DESCRIPTION", amdDto.getDescription());
        Assertions.assertEquals(metaData.getLabels(), amdDto.getLabels());
    }

    @Test
    public void testCreateArtifactWithLargeMetaData() throws Exception {
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);

        // Test creating an artifact with meta-data that is too large for the DB
        String artifactId = "testCreateArtifactWithLargeMetaData";
        EditableVersionMetaDataDto metaData = new EditableVersionMetaDataDto();
        metaData.setName(generateString(600));
        metaData.setDescription(generateString(2000));
        metaData.setLabels(new HashMap<>());
        metaData.getLabels().put("key-" + generateString(300), "value-" + generateString(2000));
        ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                null, null, ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(content).build(),
                metaData, Collections.emptyList(), false, false, null).getValue();
        dto = storage().getArtifactVersionMetaData(dto.getGlobalId());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals(512, dto.getName().length());
        Assertions.assertEquals(1024, dto.getDescription().length());
        Assertions.assertTrue(dto.getDescription().endsWith("..."));
        Assertions.assertNotNull(dto.getLabels());
        Assertions.assertEquals(1, dto.getLabels().size());
    }

    /**
     * Regression test for the outbox aggregate id length. Creating an artifact fires an outbox event whose
     * aggregate id is "groupId-artifactId" (and "groupId-artifactId-version" for the version event). That
     * INSERT happens in the same transaction as the artifact write, so if the aggregateid column is too
     * narrow the whole creation is rolled back and the artifact never appears.
     *
     * <p>
     * Uses 130-char identifiers rather than the schema maximum (512/512/256) because MSSQL's
     * branch_versions table has a composite clustered PK on (groupId, artifactId, branchId, version),
     * all NVARCHAR (2 bytes/char), and MSSQL limits clustered index keys to 900 bytes. The resulting
     * aggregate id of 393 chars still well exceeds the old 255-char column limit that caused the bug.
     * On Postgres and MSSQL this exercises the real outbox INSERT; on H2 there is no outbox table, so
     * it just confirms that long identifiers round-trip.
     */
    @Test
    public void testCreateArtifactWithMaxLengthIdentifiers() throws Exception {
        // Use 130 chars for groupId/artifactId/version because MSSQL clustered indexes have a 900-byte
        // key limit. The branch_versions table has a composite PK on (groupId, artifactId, branchId,
        // version), all NVARCHAR (2 bytes/char). With branchId ~6 chars ("latest"), the sum must stay
        // under 450 chars. An aggregate id of 130 + 1 + 130 + 1 + 130 = 393 still well exceeds the old
        // 255-char column limit that caused the original bug.
        String longGroupId = generateString(130);
        String longArtifactId = generateString(130);
        String longVersion = generateString(130);
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);

        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(longGroupId, longArtifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        EditableVersionMetaDataDto.builder().build(), Collections.emptyList(), false, false,
                        null)
                .getValue();

        // The version event's aggregate id is the widest one the application generates.
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(longGroupId, dto.getGroupId());
        Assertions.assertEquals(longArtifactId, dto.getArtifactId());
        Assertions.assertEquals(130, dto.getGroupId().length());
        Assertions.assertEquals(130, dto.getArtifactId().length());

        // The artifact must really be there a rolled back transaction would leave nothing behind.
        ArtifactMetaDataDto amdDto = storage().getArtifactMetaData(longGroupId, longArtifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals(longGroupId, amdDto.getGroupId());
        Assertions.assertEquals(longArtifactId, amdDto.getArtifactId());

        // Now add a version with a max-length version string: aggregate id is 130 + 1 + 130 + 1 + 130 = 393.
        ContentHandle contentV2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactVersionMetaDataDto v2 = storage().createArtifactVersion(longGroupId, longArtifactId,
                longVersion, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentV2).build(),
                null, Collections.emptyList(), false, false, null);

        Assertions.assertNotNull(v2);
        Assertions.assertEquals(longGroupId, v2.getGroupId());
        Assertions.assertEquals(longArtifactId, v2.getArtifactId());
        Assertions.assertEquals(longVersion.length(), v2.getVersion().length());
        int aggregateIdLength = v2.getGroupId().length() + 1 + v2.getArtifactId().length() + 1
                + v2.getVersion().length();
        Assertions.assertTrue(aggregateIdLength > 255,
                "Aggregate id length " + aggregateIdLength + " must exceed the old 255-char column limit");

        // Both versions survived, and the long version is readable by its identifiers.
        ArtifactVersionMetaDataDto readBack = storage().getArtifactVersionMetaData(longGroupId,
                longArtifactId, longVersion);
        Assertions.assertNotNull(readBack);
        Assertions.assertEquals(v2.getGlobalId(), readBack.getGlobalId());
        Assertions.assertEquals(longVersion, readBack.getVersion());

        StoredArtifactVersionDto storedVersion = storage().getArtifactVersionContent(longGroupId,
                longArtifactId, longVersion);
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT_V2, storedVersion.getContent().content());

        List<String> versions = storage().getArtifactVersions(longGroupId, longArtifactId);
        Assertions.assertEquals(2, versions.size());
        Assertions.assertTrue(versions.contains(longVersion));
    }

    @Test
    public void testCreateDuplicateArtifact() throws Exception {
        String artifactId = "testCreateDuplicateArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);

        // Should throw error for duplicate artifact.
        Assertions.assertThrows(ArtifactAlreadyExistsException.class, () -> {
            storage().createArtifact(
                    GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null, ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                    null, Collections.emptyList(), false, false, null).getValue();

        });
    }

    @Test
    public void testArtifactNotFound() throws Exception {
        String artifactId = "testArtifactNotFound-1";

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactMetaData(GROUP_ID, artifactId);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");
        });

        Assertions.assertThrows(VersionNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");
        });
    }

    @Test
    public void testCreateArtifactVersion() throws Exception {
        String artifactId = "testCreateArtifactVersion-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        List<String> versions = storage().getArtifactVersions(GROUP_ID, artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactVersionMetaDataDto dtov2 = storage().createArtifactVersion(
                GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentv2).build(),
                null, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(GROUP_ID, dtov2.getGroupId());
        Assertions.assertEquals(artifactId, dtov2.getArtifactId());
        Assertions.assertEquals("2", dtov2.getVersion());

        versions = storage().getArtifactVersions(GROUP_ID, artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());
    }

    @Test
    public void testGetArtifactVersions() throws Exception {
        String artifactId = "testGetArtifactVersions";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        StoredArtifactVersionDto storedArtifact = storage().getArtifactVersionContent(GROUP_ID, artifactId,
                "1");
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        storedArtifact = storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        storedArtifact = storage().getArtifactVersionContent(dto.getGlobalId());
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        ArtifactVersionMetaDataDto dtov1 = storage().getArtifactVersionMetaData(dto.getGlobalId());
        verifyArtifactMetadata(dtov1, dto);

        List<String> versions = storage().getArtifactVersions(GROUP_ID, artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactVersionMetaDataDto dtov2 = storage().createArtifactVersion(
                GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentv2).build(),
                null, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(GROUP_ID, dtov2.getGroupId());
        Assertions.assertEquals(artifactId, dtov2.getArtifactId());
        Assertions.assertEquals("2", dtov2.getVersion());

        versions = storage().getArtifactVersions(GROUP_ID, artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());

        // verify version 2

        storedArtifact = storage().getArtifactVersionContent(GROUP_ID, artifactId, "2");
        verifyArtifact(storedArtifact, OPENAPI_CONTENT_V2, dtov2);

        storedArtifact = storage().getArtifactVersionContent(dtov2.getGlobalId());
        verifyArtifact(storedArtifact, OPENAPI_CONTENT_V2, dtov2);

        ArtifactVersionMetaDataDto dtov2Stored = storage().getArtifactVersionMetaData(dtov2.getGlobalId());
        verifyArtifactMetadata(dtov2Stored, dtov2);

        // verify version 1 again

        storedArtifact = storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        storedArtifact = storage().getArtifactVersionContent(dto.getGlobalId());
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        dtov1 = storage().getArtifactVersionMetaData(dto.getGlobalId());
        verifyArtifactMetadata(dtov1, dto);

    }

    private void verifyArtifact(StoredArtifactVersionDto storedArtifact, String content,
            ArtifactVersionMetaDataDto expectedMetadata) {
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(content, storedArtifact.getContent().content());
        Assertions.assertEquals(expectedMetadata.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(expectedMetadata.getVersion(), storedArtifact.getVersion());
    }

    private void verifyArtifactMetadata(ArtifactVersionMetaDataDto actualMetadata,
            ArtifactVersionMetaDataDto expectedMetadata) {
        Assertions.assertNotNull(actualMetadata);
        Assertions.assertNotNull(expectedMetadata);
        Assertions.assertEquals(expectedMetadata.getName(), actualMetadata.getName());
        Assertions.assertEquals(expectedMetadata.getDescription(), actualMetadata.getDescription());
    }

    @Test
    public void testCreateArtifactVersionWithMetaData() throws Exception {
        String artifactId = "testCreateArtifactVersionWithMetaData-2";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        List<String> versions = storage().getArtifactVersions(GROUP_ID, artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        EditableVersionMetaDataDto metaData = EditableVersionMetaDataDto.builder().name("NAME")
                .description("DESCRIPTION").labels(Collections.singletonMap("K", "V")).build();
        ArtifactVersionMetaDataDto dtov2 = storage().createArtifactVersion(
                GROUP_ID, artifactId, "2", ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentv2).build(),
                metaData, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(GROUP_ID, dtov2.getGroupId());
        Assertions.assertEquals(artifactId, dtov2.getArtifactId());
        Assertions.assertEquals("2", dtov2.getVersion());
        Assertions.assertEquals("NAME", dtov2.getName());
        Assertions.assertEquals("DESCRIPTION", dtov2.getDescription());
        Assertions.assertEquals(metaData.getLabels(), dtov2.getLabels());

        versions = storage().getArtifactVersions(GROUP_ID, artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());

        ArtifactVersionMetaDataDto vmd = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "2");
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals("NAME", vmd.getName());
        Assertions.assertEquals("DESCRIPTION", vmd.getDescription());
    }

    @Test
    public void testGetArtifactMetaDataByGlobalId() throws Exception {
        String artifactId = "testGetArtifactMetaDataByGlobalId-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto versionMetaDataDto = EditableVersionMetaDataDto.builder().name("Empty API")
                .description("An example API design using OpenAPI.").build();
        ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                null, null, ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(content).build(),
                versionMetaDataDto, Collections.emptyList(), false, false, null).getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertEquals("1", dto.getVersion());

        long globalId = dto.getGlobalId();

        dto = storage().getArtifactVersionMetaData(globalId);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertEquals("1", dto.getVersion());
    }

    @Test
    public void testUpdateArtifactMetaData() throws Exception {
        String artifactId = "testUpdateArtifactMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto versionMetaDataDto = EditableVersionMetaDataDto.builder().name("Empty API")
                .description("An example API design using OpenAPI.").build();
        ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                null, null, ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(content).build(),
                versionMetaDataDto, Collections.emptyList(), false, false, null).getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertEquals("1", dto.getVersion());

        String newName = "Updated Name";
        String newDescription = "Updated description.";
        Map<String, String> newLabels = new HashMap<>();
        newLabels.put("foo", "bar");
        newLabels.put("ting", "bin");
        EditableArtifactMetaDataDto emd = new EditableArtifactMetaDataDto(newName, newDescription, null,
                newLabels);
        storage().updateArtifactMetaData(GROUP_ID, artifactId, emd);

        ArtifactMetaDataDto metaData = storage().getArtifactMetaData(GROUP_ID, artifactId);
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(newName, metaData.getName());
        Assertions.assertEquals(newDescription, metaData.getDescription());
    }

    @Test
    public void testUpdateArtifactVersionState() throws Exception {
        String artifactId = "testUpdateArtifactVersionState-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactVersionMetaDataDto dtov2 = storage().createArtifactVersion(
                GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentv2).build(),
                null, Collections.emptyList(), false, false, null);
        ;
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(GROUP_ID, dtov2.getGroupId());
        Assertions.assertEquals(artifactId, dtov2.getArtifactId());
        Assertions.assertEquals("2", dtov2.getVersion());

        updateVersionState(GROUP_ID, artifactId, "1", VersionState.DISABLED);
        updateVersionState(GROUP_ID, artifactId, "2", VersionState.DEPRECATED);

        ArtifactVersionMetaDataDto v1 = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");
        ArtifactVersionMetaDataDto v2 = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "2");
        Assertions.assertNotNull(v1);
        Assertions.assertNotNull(v2);
        Assertions.assertEquals(VersionState.DISABLED, v1.getState());
        Assertions.assertEquals(VersionState.DEPRECATED, v2.getState());
    }

    @Test
    public void testUpdateArtifactVersionMetaData() throws Exception {
        String artifactId = "testUpdateArtifactVersionMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto versionMetaDataDto = EditableVersionMetaDataDto.builder().name("Empty API")
                .description("An example API design using OpenAPI.").build();
        String staleOwner = "stale-creation-user";
        ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                null, null, ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(content).build(),
                versionMetaDataDto, Collections.emptyList(), false, false, staleOwner).getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertEquals("1", dto.getVersion());
        Assertions.assertEquals(staleOwner, dto.getModifiedBy());

        String newName = "Updated Name";
        String newDescription = "Updated description.";
        Map<String, String> newLabels = new HashMap<>();
        newLabels.put("foo", "bar");
        newLabels.put("ting", "bin");
        EditableVersionMetaDataDto emd = new EditableVersionMetaDataDto(newName, newDescription, newLabels);
        storage().updateArtifactVersionMetaData(GROUP_ID, artifactId, "1", emd);

        ArtifactVersionMetaDataDto metaData = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(newName, metaData.getName());
        Assertions.assertEquals(newDescription, metaData.getDescription());
    }

    @Test
    public void testUpdateArtifactVersionMetaDataNameOnlyUpdatesModified() throws Exception {
        String artifactId = "testUpdateArtifactVersionMetaDataNameOnly-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto versionMetaDataDto = EditableVersionMetaDataDto.builder().name("Empty API")
                .build();
        String staleOwner = "stale-creation-user";
        storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                versionMetaDataDto, Collections.emptyList(), false, false, staleOwner);

        ArtifactVersionMetaDataDto before = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");
        Assertions.assertEquals(staleOwner, before.getModifiedBy());

        storage().updateArtifactVersionMetaData(GROUP_ID, artifactId, "1",
                EditableVersionMetaDataDto.builder().name("Name Only Update").build());

        ArtifactVersionMetaDataDto after = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");
        Assertions.assertEquals("Name Only Update", after.getName());
        // modifiedBy must move off the stale creation owner, proving the audit update ran
        Assertions.assertNotEquals(staleOwner, after.getModifiedBy());
        Assertions.assertTrue(after.getModifiedOn() >= before.getModifiedOn());
    }

    @Test
    public void testUpdateArtifactVersionMetaDataEmptyUpdateDoesNotModify() throws Exception {
        String artifactId = "testUpdateArtifactVersionMetaDataEmpty-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto versionMetaDataDto = EditableVersionMetaDataDto.builder().name("Empty API")
                .build();
        storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                versionMetaDataDto, Collections.emptyList(), false, false, null);

        ArtifactVersionMetaDataDto before = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");

        // An update with no fields set is a no-op: audit fields must be left untouched.
        storage().updateArtifactVersionMetaData(GROUP_ID, artifactId, "1",
                EditableVersionMetaDataDto.builder().build());

        ArtifactVersionMetaDataDto after = storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");
        Assertions.assertEquals(before.getModifiedBy(), after.getModifiedBy());
        Assertions.assertEquals(before.getModifiedOn(), after.getModifiedOn());
    }

    @Test
    public void testDeleteArtifact() throws Exception {
        String artifactId = "testDeleteArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableVersionMetaDataDto versionMetaDataDto = EditableVersionMetaDataDto.builder().name("Empty API")
                .description("An example API design using OpenAPI.").build();
        ArtifactVersionMetaDataDto dto = storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                null, null, ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(content).build(),
                versionMetaDataDto, Collections.emptyList(), false, false, null).getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertEquals("1", dto.getVersion());

        storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");

        storage().deleteArtifact(GROUP_ID, artifactId);

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactMetaData(GROUP_ID, artifactId);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, artifactId, "1");
        });
        Assertions.assertThrows(VersionNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(GROUP_ID, artifactId, "1");
        });
    }

    @Test
    public void testDeleteArtifactVersion() throws Exception {
        // Delete the only version
        ////////////////////////////
        String artifactId = "testDeleteArtifactVersion-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("1", dto.getVersion());

        storage().deleteArtifactVersion(GROUP_ID, artifactId, "1");

        final String aid1 = artifactId;
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, aid1, "1");
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, aid1, "1");
        });
        Assertions.assertThrows(VersionNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(GROUP_ID, aid1, "1");
        });

        // Delete one of multiple versions
        artifactId = "testDeleteArtifactVersion-2";
        content = ContentHandle.create(OPENAPI_CONTENT);
        dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("1", dto.getVersion());

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactVersionMetaDataDto dtov2 = storage().createArtifactVersion(
                GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentv2).build(),
                null, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals("2", dtov2.getVersion());

        storage().deleteArtifactVersion(GROUP_ID, artifactId, "1");

        final String aid2 = artifactId;

        storage().getArtifactMetaData(GROUP_ID, aid2);
        storage().getArtifactVersionContent(GROUP_ID, aid2, "2");
        storage().getArtifactVersionMetaData(GROUP_ID, aid2, "2");
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, aid2, "1");
        });
        Assertions.assertThrows(VersionNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(GROUP_ID, aid2, "1");
        });

        ArtifactVersionMetaDataDto dtov3 = storage().createArtifactVersion(
                GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                null, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtov3);
        Assertions.assertEquals("3", dtov3.getVersion());

        // Update version 2 to DISABLED state and delete latest version
        updateVersionState(GROUP_ID, artifactId, "2", VersionState.DISABLED);
        storage().deleteArtifactVersion(GROUP_ID, artifactId, "3");

        GAV latestGAV = storage().getBranchTip(new GA(GROUP_ID, artifactId), BranchId.LATEST,
                RetrievalBehavior.ALL_STATES);
        ArtifactVersionMetaDataDto artifactMetaData = storage().getArtifactVersionMetaData(GROUP_ID, aid2,
                latestGAV.getRawVersionId());
        Assertions.assertNotNull(artifactMetaData);
        Assertions.assertEquals("2", artifactMetaData.getVersion());
        Assertions.assertEquals(aid2, artifactMetaData.getArtifactId());

        // Delete the latest version
        artifactId = "testDeleteArtifactVersion-3";
        content = ContentHandle.create(OPENAPI_CONTENT);
        dto = storage().createArtifact(
                GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentv2).build(),
                null, Collections.emptyList(), false, false, null).getValue();

        Assertions.assertNotNull(dto);
        Assertions.assertEquals("1", dto.getVersion());

        contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        dtov2 = storage().createArtifactVersion(
                GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(contentv2).build(),
                null, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtov2);

        final String aid3 = artifactId;
        storage().deleteArtifactVersion(GROUP_ID, aid3, "2");
        List<String> versions = storage().getArtifactVersions(GROUP_ID, aid3);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());
        Assertions.assertEquals("1", versions.iterator().next());

        VersionSearchResultsDto result = storage().searchVersions(
                Set.of(SearchFilter.ofGroupId(GROUP_ID), SearchFilter.ofArtifactId(aid3)), OrderBy.groupId,
                OrderDirection.asc, 0, 10, false);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getCount());
        Assertions.assertEquals("1", result.getVersions().iterator().next().getVersion());

        artifactMetaData = storage().getArtifactVersionMetaData(GROUP_ID, aid3, "1");
        Assertions.assertNotNull(artifactMetaData);
        Assertions.assertEquals("1", artifactMetaData.getVersion());
        Assertions.assertEquals(aid3, artifactMetaData.getArtifactId());

        storage().getArtifactVersionContent(GROUP_ID, aid3, "1");
        ArtifactVersionMetaDataDto metaData = storage().getArtifactVersionMetaData(GROUP_ID, aid3, "1");
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals("1", metaData.getVersion());
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionContent(GROUP_ID, aid3, "2");
        });
        Assertions.assertThrows(VersionNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(GROUP_ID, aid3, "2");
        });
    }

    @Test
    public void testCreateArtifactRule() throws Exception {
        String artifactId = "testCreateArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        List<RuleType> artifactRules = storage().getArtifactRules(GROUP_ID, artifactId);
        Assertions.assertNotNull(artifactRules);
        Assertions.assertTrue(artifactRules.isEmpty());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY, configDto);

        artifactRules = storage().getArtifactRules(GROUP_ID, artifactId);
        Assertions.assertNotNull(artifactRules);
        Assertions.assertFalse(artifactRules.isEmpty());
        Assertions.assertEquals(1, artifactRules.size());
        Assertions.assertEquals(RuleType.VALIDITY, artifactRules.get(0));
    }

    @Test
    public void testUpdateArtifactRule() throws Exception {
        String artifactId = "testUpdateArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY, configDto);

        RuleConfigurationDto rule = storage().getArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("FULL", rule.getConfiguration());

        RuleConfigurationDto updatedConfig = new RuleConfigurationDto("NONE");
        storage().updateArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY, updatedConfig);

        rule = storage().getArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("NONE", rule.getConfiguration());
    }

    @Test
    public void testDeleteArtifactRule() throws Exception {
        String artifactId = "testDeleteArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY, configDto);

        RuleConfigurationDto rule = storage().getArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("FULL", rule.getConfiguration());

        storage().deleteArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY);

        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage().getArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY);
        });
    }

    @Test
    public void testDeleteAllArtifactRules() throws Exception {
        String artifactId = "testDeleteAllArtifactRulse-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY, configDto);
        storage().createArtifactRule(GROUP_ID, artifactId, RuleType.COMPATIBILITY, configDto);

        List<RuleType> rules = storage().getArtifactRules(GROUP_ID, artifactId);
        Assertions.assertEquals(2, rules.size());

        storage().deleteArtifactRules(GROUP_ID, artifactId);

        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage().getArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY);
        });
        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage().getArtifactRule(GROUP_ID, artifactId, RuleType.COMPATIBILITY);
        });
    }

    @Test
    public void testGlobalRules() {
        List<RuleType> globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());

        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration("FULL");
        storage().createGlobalRule(RuleType.COMPATIBILITY, config);

        RuleConfigurationDto rule = storage().getGlobalRule(RuleType.COMPATIBILITY);
        Assertions.assertEquals(rule.getConfiguration(), config.getConfiguration());

        globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertFalse(globalRules.isEmpty());
        Assertions.assertEquals(globalRules.size(), 1);
        Assertions.assertEquals(globalRules.get(0), RuleType.COMPATIBILITY);

        Assertions.assertThrows(RuleAlreadyExistsException.class, () -> {
            storage().createGlobalRule(RuleType.COMPATIBILITY, config);
        });

        RuleConfigurationDto updatedConfig = new RuleConfigurationDto("FORWARD");
        storage().updateGlobalRule(RuleType.COMPATIBILITY, updatedConfig);

        rule = storage().getGlobalRule(RuleType.COMPATIBILITY);
        Assertions.assertEquals(rule.getConfiguration(), updatedConfig.getConfiguration());

        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage().updateGlobalRule(RuleType.VALIDITY, config);
        });

        storage().deleteGlobalRules();
        globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());

        storage().createGlobalRule(RuleType.COMPATIBILITY, config);
        storage().deleteGlobalRule(RuleType.COMPATIBILITY);
        globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());
    }

    @Test
    public void testSearchGroups() throws Exception {
        String groupIdPrefix = "testSearchGroups-";
        for (int idx = 1; idx <= 50; idx++) {
            String idxs = (idx < 10 ? "0" : "") + idx;
            String groupId = groupIdPrefix + idxs;
            Map<String, String> labels = Collections.singletonMap("key", "value-" + idx);

            GroupMetaDataDto groupMetaDataDto = GroupMetaDataDto.builder()
                    .description(groupId + "-description").groupId(groupId).labels(labels).build();

            storage().createGroup(groupMetaDataDto);
        }

        long start = System.currentTimeMillis();

        Set<SearchFilter> filters = Collections.emptySet();
        GroupSearchResultsDto results = storage().searchGroups(filters, OrderBy.groupId, OrderDirection.asc,
                0, 10);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(51, results.getCount());
        Assertions.assertNotNull(results.getGroups());
        Assertions.assertEquals(10, results.getGroups().size());

        filters = Collections.singleton(SearchFilter.ofDescription("testSearchGroups-33-description"));
        results = storage().searchGroups(filters, OrderBy.groupId, OrderDirection.asc, 0, 10);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getGroups());
        Assertions.assertEquals(1, results.getGroups().size());

        filters = Collections.emptySet();
        results = storage().searchGroups(filters, OrderBy.groupId, OrderDirection.asc, 0, 10);
        Assertions.assertNotNull(results);
        Assertions.assertNotNull(results.getGroups());
        Assertions.assertEquals(10, results.getGroups().size());

        filters = Collections.singleton(SearchFilter.ofLabel("key", "value-17"));
        results = storage().searchGroups(filters, OrderBy.groupId, OrderDirection.asc, 0, 10);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getGroups());
        Assertions.assertEquals(1, results.getGroups().size());

        long end = System.currentTimeMillis();
        System.out.println("Search time: " + (end - start) + "ms");
    }

    @Test
    public void testSearchArtifacts() throws Exception {
        String artifactIdPrefix = "testSearchArtifacts-";
        for (int idx = 1; idx <= 50; idx++) {
            String idxs = (idx < 10 ? "0" : "") + idx;
            String artifactId = artifactIdPrefix + idxs;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            Map<String, String> labels = Collections.singletonMap("key", "value-" + idx);
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(artifactId + "-name",
                    artifactId + "-description", null, labels);
            storage().createArtifact(
                    GROUP_ID, artifactId, ArtifactType.OPENAPI, metaData, null, ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                    null, Collections.emptyList(), false, false, null).getValue();

        }

        long start = System.currentTimeMillis();

        Set<SearchFilter> filters = Collections.singleton(SearchFilter.ofName("testSearchArtifacts*"));
        ArtifactSearchResultsDto results = storage().searchArtifacts(filters, OrderBy.name,
                OrderDirection.asc, 0, 10, false);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(50, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(10, results.getArtifacts().size());

        filters = Collections.singleton(SearchFilter.ofName("testSearchArtifacts-19-name"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-19-name", results.getArtifacts().get(0).getName());

        filters = Collections.singleton(SearchFilter.ofDescription("testSearchArtifacts-33-description"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-33-name", results.getArtifacts().get(0).getName());

        filters = Collections.emptySet();
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false);
        Assertions.assertNotNull(results);
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(10, results.getArtifacts().size());

        filters = Collections.singleton(SearchFilter.ofLabel("key", "value-17"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-17-name", results.getArtifacts().get(0).getName());

        long end = System.currentTimeMillis();
        System.out.println("Search time: " + (end - start) + "ms");
    }

    @Test
    public void testSearchVersions() throws Exception {
        String artifactId = "testSearchVersions-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        // Add more versions
        for (int idx = 2; idx <= 50; idx++) {
            content = ContentHandle.create(OPENAPI_CONTENT_TEMPLATE.replaceAll("VERSION", "1.0." + idx));
            EditableVersionMetaDataDto metaData = new EditableVersionMetaDataDto(artifactId + "-name-" + idx,
                    artifactId + "-description-" + idx, null);
            storage().createArtifactVersion(
                    GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                    metaData, Collections.emptyList(), false, false, null);

        }

        TestUtils.retry(() -> {
            VersionSearchResultsDto results = storage().searchVersions(
                    Set.of(SearchFilter.ofGroupId(GROUP_ID), SearchFilter.ofArtifactId(artifactId)),
                    OrderBy.groupId, OrderDirection.asc, 0, 10, false);
            Assertions.assertNotNull(results);
            Assertions.assertEquals(50, results.getCount());
            Assertions.assertEquals(10, results.getVersions().size());

            results = storage().searchVersions(
                    Set.of(SearchFilter.ofGroupId(GROUP_ID), SearchFilter.ofArtifactId(artifactId)),
                    OrderBy.groupId, OrderDirection.asc, 0, 50, false);
            Assertions.assertNotNull(results);
            Assertions.assertEquals(50, results.getCount());
            Assertions.assertEquals(50, results.getVersions().size());
        });
    }

    @Test
    public void testSearchVersionsByNegatedGlobalIdAndContentId() throws Exception {
        String artifactId = "testSearchVersionsByNegatedGlobalIdAndContentId-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);

        content = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactVersionMetaDataDto dtov2 = storage().createArtifactVersion(
                GROUP_ID, artifactId, null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                null, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtov2);

        TestUtils.retry(() -> {
            VersionSearchResultsDto results = storage().searchVersions(
                    Set.of(SearchFilter.ofGroupId(GROUP_ID), SearchFilter.ofArtifactId(artifactId),
                            SearchFilter.ofGlobalId(dto.getGlobalId()).negated()),
                    OrderBy.globalId, OrderDirection.asc, 0, 10, false);
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.getCount());
            Assertions.assertEquals(1, results.getVersions().size());
            Assertions.assertEquals(dtov2.getGlobalId(), results.getVersions().get(0).getGlobalId());

            results = storage().searchVersions(
                    Set.of(SearchFilter.ofGroupId(GROUP_ID), SearchFilter.ofArtifactId(artifactId),
                            SearchFilter.ofContentId(dto.getContentId()).negated()),
                    OrderBy.globalId, OrderDirection.asc, 0, 10, false);
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.getCount());
            Assertions.assertEquals(1, results.getVersions().size());
            Assertions.assertEquals(dtov2.getContentId(), results.getVersions().get(0).getContentId());

            results = storage().searchVersions(
                    Set.of(SearchFilter.ofGroupId(GROUP_ID), SearchFilter.ofArtifactId(artifactId),
                            SearchFilter.ofGlobalId(dtov2.getGlobalId()),
                            SearchFilter.ofContentId(dtov2.getContentId())),
                    OrderBy.globalId, OrderDirection.asc, 0, 10, false);
            Assertions.assertNotNull(results);
            Assertions.assertEquals(1, results.getCount());
            Assertions.assertEquals(1, results.getVersions().size());
            Assertions.assertEquals(dtov2.getGlobalId(), results.getVersions().get(0).getGlobalId());
            Assertions.assertEquals(dtov2.getContentId(), results.getVersions().get(0).getContentId());
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> storage().searchVersions(
                Set.of(SearchFilter.ofGlobalId(null)), OrderBy.globalId, OrderDirection.asc, 0, 10,
                false));

        SearchFilter invalidGlobalId = new SearchFilter();
        invalidGlobalId.setType(SearchFilterType.globalId);
        invalidGlobalId.setStringValue("not-a-number");
        Assertions.assertThrows(IllegalStateException.class, () -> storage().searchVersions(
                Set.of(invalidGlobalId), OrderBy.globalId, OrderDirection.asc, 0, 10, false));
    }

    @Test
    public void testVersionSortingAndSemver() throws Exception {
        String artifactId = "testSemverSorting-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        storage().createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                null, Collections.emptyList(), false, false, null);

        String[] versionsToInsert = { "2", "10", "1.0.0-10", "1.0.0-9", "1.0.0-alpha", "1.0.1", "latest", "zzz-custom" };
        
        for (String ver : versionsToInsert) {
            storage().createArtifactVersion(GROUP_ID, artifactId, ver, ArtifactType.OPENAPI,
                    ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                    null, Collections.emptyList(), false, false, null);
        }

        TestUtils.retry(() -> {
            VersionSearchResultsDto results = storage().searchVersions(
                    Set.of(SearchFilter.ofGroupId(GROUP_ID), SearchFilter.ofArtifactId(artifactId)),
                    OrderBy.version, OrderDirection.asc, 0, 10, false);

            Assertions.assertNotNull(results);
            Assertions.assertEquals(9, results.getCount());
            
            List<SearchedVersionDto> sortedVersions = results.getVersions();
            
            // Validate the mathematically correct SemVer sorting order:
            Assertions.assertEquals("1.0.0-9", sortedVersions.get(0).getVersion());
            Assertions.assertEquals("1.0.0-10", sortedVersions.get(1).getVersion());
            Assertions.assertEquals("1.0.0-alpha", sortedVersions.get(2).getVersion());
            Assertions.assertEquals("1", sortedVersions.get(3).getVersion());
            Assertions.assertEquals("1.0.1", sortedVersions.get(4).getVersion());
            Assertions.assertEquals("2", sortedVersions.get(5).getVersion());
            Assertions.assertEquals("10", sortedVersions.get(6).getVersion());
            Assertions.assertEquals("latest", sortedVersions.get(7).getVersion());
            Assertions.assertEquals("zzz-custom", sortedVersions.get(8).getVersion());
        });
    }

    private void createSomeUserData() {
        final String group1 = "testGroup-1";
        final String group2 = "testGroup-2";
        final String artifactId1 = "testArtifact-1";
        final String artifactId2 = "testArtifact-2";
        final String principal = "testPrincipal";
        final String role = "testRole";

        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        storage().createGroup(GroupMetaDataDto.builder().groupId(group1).build());
        ArtifactVersionMetaDataDto artifactDto1 = storage()
                .createArtifact(group1, artifactId1, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        storage().createArtifactRule(group1, artifactId1, RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("FULL").build());
        ArtifactVersionMetaDataDto artifactDto2 = storage()
                .createArtifact(group2, artifactId2, ArtifactType.OPENAPI,
                        EditableArtifactMetaDataDto.builder().name("test").build(), null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();

        storage().createGlobalRule(RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("FULL").build());
        storage().createRoleMapping(principal, role, null);

        // Verify data exists

        Assertions.assertNotNull(
                storage().getArtifactVersionContent(group1, artifactId1, artifactDto1.getVersion()));
        Assertions.assertEquals(1, storage().getArtifactRules(group1, artifactId1).size());
        Assertions.assertNotNull(
                storage().getArtifactVersionContent(group2, artifactId2, artifactDto2.getVersion()));
        Assertions.assertEquals(1, storage().getGlobalRules().size());
        Assertions.assertEquals(role, storage().getRoleForPrincipal(principal));
    }

    private int countStorageEntities() {
        // We don't need thread safety, but it's simpler to use this when effectively final counter is needed
        final AtomicInteger count = new AtomicInteger(0);
        storage().exportData(null, e -> {
            if (e.getEntityType() != EntityType.Manifest) {
                log.debug("Counting from export: {}", e);
                count.incrementAndGet();
            }
            return null;
        });
        int res = count.get();
        // Count data that is not exported
        res += storage().getRoleMappings().size();
        return res;
    }

    @Test
    public void testDeleteAllUserData() {
        // Delete first to cleanup after other tests
        storage().deleteAllUserData();
        createSomeUserData();
        Assertions.assertEquals(12, countStorageEntities());
        storage().deleteAllUserData();
        Assertions.assertEquals(0, countStorageEntities());
    }

    @Test
    public void testConfigProperties() throws Exception {
        List<DynamicConfigPropertyDto> properties = storage().getConfigProperties();
        Assertions.assertNotNull(properties);
        Assertions.assertTrue(properties.isEmpty());

        storage().setConfigProperty(
                new DynamicConfigPropertyDto("apicurio.test.property-string", "test-value"));
        storage().setConfigProperty(new DynamicConfigPropertyDto("apicurio.test.property-boolean", "true"));
        storage().setConfigProperty(new DynamicConfigPropertyDto("apicurio.test.property-long", "12345"));

        properties = storage().getConfigProperties();
        Assertions.assertNotNull(properties);
        Assertions.assertFalse(properties.isEmpty());
        Assertions.assertEquals(3, properties.size());

        DynamicConfigPropertyDto stringProp = getProperty(properties, "apicurio.test.property-string");
        DynamicConfigPropertyDto boolProp = getProperty(properties, "apicurio.test.property-boolean");
        DynamicConfigPropertyDto longProp = getProperty(properties, "apicurio.test.property-long");

        Assertions.assertNotNull(stringProp);
        Assertions.assertNotNull(boolProp);
        Assertions.assertNotNull(longProp);

        Assertions.assertEquals("test-value", stringProp.getValue());
        Assertions.assertEquals("true", boolProp.getValue());
        Assertions.assertEquals("12345", longProp.getValue());

        // Set the same property again (UPSERT path): must update, not duplicate or error
        storage().setConfigProperty(
                new DynamicConfigPropertyDto("apicurio.test.property-string", "updated-value"));
        properties = storage().getConfigProperties();
        Assertions.assertEquals(3, properties.size(),
                "Setting the same property again must update it, not create a duplicate");
        DynamicConfigPropertyDto updatedProp = getProperty(properties, "apicurio.test.property-string");
        Assertions.assertNotNull(updatedProp);
        Assertions.assertEquals("updated-value", updatedProp.getValue());
    }

    private DynamicConfigPropertyDto getProperty(List<DynamicConfigPropertyDto> properties,
            String propertyName) {
        for (DynamicConfigPropertyDto prop : properties) {
            if (prop.getName().equals(propertyName)) {
                return prop;
            }
        }
        return null;
    }

    @Test
    public void testComments() {
        String artifactId = "testComments-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dto = storage()
                .createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(GROUP_ID, dto.getGroupId());
        Assertions.assertEquals(artifactId, dto.getArtifactId());

        List<CommentDto> comments = storage().getArtifactVersionComments(GROUP_ID, artifactId,
                dto.getVersion());
        Assertions.assertTrue(comments.isEmpty());

        storage().createArtifactVersionComment(GROUP_ID, artifactId, dto.getVersion(), "TEST_COMMENT_1");
        storage().createArtifactVersionComment(GROUP_ID, artifactId, dto.getVersion(), "TEST_COMMENT_2");
        storage().createArtifactVersionComment(GROUP_ID, artifactId, dto.getVersion(), "TEST_COMMENT_3");

        comments = storage().getArtifactVersionComments(GROUP_ID, artifactId, dto.getVersion());
        Assertions.assertEquals(3, comments.size());

        storage().deleteArtifactVersionComment(GROUP_ID, artifactId, dto.getVersion(),
                comments.get(1).getCommentId());

        comments = storage().getArtifactVersionComments(GROUP_ID, artifactId, dto.getVersion());
        Assertions.assertEquals(2, comments.size());

        storage().updateArtifactVersionComment(GROUP_ID, artifactId, dto.getVersion(),
                comments.get(0).getCommentId(), "TEST_COMMENT_4");

        comments = storage().getArtifactVersionComments(GROUP_ID, artifactId, dto.getVersion());
        Assertions.assertEquals(2, comments.size());
        Assertions.assertEquals("TEST_COMMENT_4", comments.get(0).getValue());
    }

    @Test
    public void testBranches() {

        var ga = new GA(GROUP_ID, "foo");

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> storage().getBranches(ga, 0, 100));

        var content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactVersionMetaDataDto dtoV1 = storage()
                .createArtifact(GROUP_ID, ga.getRawArtifactId(), ArtifactType.OPENAPI, null, null,
                        ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                                .content(content).build(),
                        null, Collections.emptyList(), false, false, null)
                .getValue();

        Assertions.assertNotNull(dtoV1);
        Assertions.assertEquals(ga.getRawGroupIdWithDefaultString(), dtoV1.getGroupId());
        Assertions.assertEquals(ga.getRawArtifactId(), dtoV1.getArtifactId());

        var branches = storage().getBranches(ga, 0, 100);
        SearchedBranchDto branch = branches.getBranches().get(0);
        Assertions.assertEquals(BranchId.LATEST.getRawBranchId(), branch.getBranchId());
        Assertions.assertEquals(ga.getRawGroupIdWithDefaultString(), branch.getGroupId());
        Assertions.assertEquals(ga.getRawArtifactId(), branch.getArtifactId());

        var latestBranch = storage().getBranchTip(ga, BranchId.LATEST, RetrievalBehavior.ALL_STATES);
        Assertions.assertEquals(new GAV(ga, dtoV1.getVersion()), latestBranch);

        var gavV1 = storage().getBranchTip(ga, BranchId.LATEST, RetrievalBehavior.ALL_STATES);
        Assertions.assertNotNull(gavV1);
        Assertions.assertEquals(gavV1.getRawGroupIdWithDefaultString(), dtoV1.getGroupId());
        Assertions.assertEquals(gavV1.getRawArtifactId(), dtoV1.getArtifactId());
        Assertions.assertEquals(gavV1.getRawVersionId(), dtoV1.getVersion());

        var otherBranchId = new BranchId("other");
        storage().createBranch(gavV1, otherBranchId, "", Collections.emptyList());

        content = ContentHandle.create(OPENAPI_CONTENT_V2);
        var dtoV2 = storage().createArtifactVersion(ga.getRawGroupIdWithDefaultString(),
                ga.getRawArtifactId(), null, ArtifactType.OPENAPI, ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                null, Collections.emptyList(), false, false, null);
        Assertions.assertNotNull(dtoV2);
        Assertions.assertEquals(ga.getRawGroupIdWithDefaultString(), dtoV2.getGroupId());
        Assertions.assertEquals(ga.getRawArtifactId(), dtoV2.getArtifactId());
        // TODO update branches test
        /*
         * branches = storage().getBranches(ga, 0, 100);
         * Assertions.assertTrue(branches.getBranches().containsAll(List.of(SearchedBranchDto.builder().
         * branchId(BranchId.LATEST).build(), SearchedBranchDto.builder().build(),
         * SearchedBranchDto.builder().build())));
         * 
         * Map.of( BranchId.LATEST, List.of(new GAV(ga, dtoV2.getVersion()), new GAV(ga, dtoV1.getVersion())),
         * otherBranchId, List.of(new GAV(ga, dtoV1.getVersion())) ), branches);
         * 
         * latestBranch = storage().getBranchTip(ga, BranchId.LATEST, RetrievalBehavior.ALL_STATES);
         * Assertions.assertEquals(List.of(new GAV(ga, dtoV2.getVersion()), new GAV(ga, dtoV1.getVersion())),
         * latestBranch);
         * 
         * var otherBranch = storage().getBranchTip(ga, otherBranchId, RetrievalBehavior.ALL_STATES);
         * Assertions.assertEquals(List.of(new GAV(ga, dtoV1.getVersion())), otherBranch);
         * 
         * var gavV2 = storage().getBranchTip(ga, BranchId.LATEST, RetrievalBehavior.ALL_STATES);
         * Assertions.assertNotNull(gavV2); Assertions.assertEquals(gavV2.getRawGroupIdWithDefaultString(),
         * dtoV2.getGroupId()); Assertions.assertEquals(gavV2.getRawArtifactId(), dtoV2.getArtifactId());
         * Assertions.assertEquals(gavV2.getRawVersionId(), dtoV2.getVersion());
         * 
         * gavV1 = storage().getBranchTip(ga, otherBranchId, RetrievalBehavior.ALL_STATES);
         * Assertions.assertNotNull(gavV1); Assertions.assertEquals(gavV1.getRawGroupIdWithDefaultString(),
         * dtoV1.getGroupId()); Assertions.assertEquals(gavV1.getRawArtifactId(), dtoV1.getArtifactId());
         * Assertions.assertEquals(gavV1.getRawVersionId(), dtoV1.getVersion());
         * 
         * storage().createBranch(gavV2, otherBranchId, "", Collections.emptyList());
         * 
         * branches = storage().getBranches(ga, 0, 100); Assertions.assertEquals(Map.of( BranchId.LATEST,
         * List.of(new GAV(ga, dtoV2.getVersion()), new GAV(ga, dtoV1.getVersion())), otherBranchId,
         * List.of(new GAV(ga, dtoV2.getVersion()), new GAV(ga, dtoV1.getVersion())) ), branches);
         * 
         * Assertions.assertEquals(storage().getBranchTip(ga, BranchId.LATEST, RetrievalBehavior.ALL_STATES),
         * storage().getBranchTip(ga, otherBranchId, RetrievalBehavior.ALL_STATES));
         * Assertions.assertEquals(storage().getBranchTip(ga, BranchId.LATEST, RetrievalBehavior.ALL_STATES),
         * storage().getBranchTip(ga, otherBranchId, RetrievalBehavior.ALL_STATES));
         * 
         * updateVersionState(gavV2.getRawGroupIdWithDefaultString(), gavV2.getRawArtifactId(),
         * gavV2.getRawVersionId(), VersionState.DISABLED); Assertions.assertEquals(List.of(gavV1),
         * storage().getBranchTip(ga, BranchId.LATEST, SKIP_DISABLED_LATEST)); Assertions.assertEquals(gavV1,
         * storage().getBranchTip(ga, BranchId.LATEST, SKIP_DISABLED_LATEST));
         * 
         * updateVersionState(gavV2.getRawGroupIdWithDefaultString(), gavV2.getRawArtifactId(),
         * gavV2.getRawVersionId(), VersionState.ENABLED); Assertions.assertEquals(List.of(gavV2, gavV1),
         * storage().getBranchTip(ga, BranchId.LATEST, SKIP_DISABLED_LATEST)); Assertions.assertEquals(gavV2,
         * storage().getBranchTip(ga, BranchId.LATEST, SKIP_DISABLED_LATEST));
         * 
         * storage().deleteArtifactVersion(gavV1.getRawGroupIdWithDefaultString(), gavV1.getRawArtifactId(),
         * gavV1.getRawVersionId());
         * 
         * Assertions.assertEquals(List.of(gavV2), storage().getBranchTip(ga, BranchId.LATEST,
         * RetrievalBehavior.ALL_STATES)); Assertions.assertEquals(List.of(gavV2), storage().getBranchTip(ga,
         * otherBranchId, RetrievalBehavior.ALL_STATES));
         * 
         * storage().deleteBranch(ga, otherBranchId);
         * 
         * Assertions.assertThrows(BranchNotFoundException.class, () -> storage().getBranchTip(ga,
         * otherBranchId, RetrievalBehavior.ALL_STATES));
         * Assertions.assertThrows(VersionNotFoundException.class, () -> storage().getBranchTip(ga,
         * otherBranchId, RetrievalBehavior.ALL_STATES));
         * 
         * Assertions.assertThrows(NotAllowedException.class, () -> storage().deleteBranch(ga,
         * BranchId.LATEST));
         */
    }

    private void updateVersionState(String groupId, String artifactId, String version,
            VersionState newState) {
        storage().updateArtifactVersionState(groupId, artifactId, version, newState, false);
    }

    private static String generateString(int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            builder.append("a");
        }
        Assertions.assertEquals(size, builder.toString().length());
        return builder.toString();
    }

    /**
     * Verifies that label-based artifact search is locale-safe. Under the Turkish locale,
     * plain .toLowerCase() maps 'I' to dotless-i (U+0131) instead of 'i', causing a
     * mismatch between the stored label key and the search filter value. All normalization
     * must use Locale.ROOT so this round-trip is consistent regardless of the JVM locale.
     *
     * <p>The label key is intentionally unique (UUID-based) so the test is isolated from
     * any pre-existing artifacts in the shared database, making it safe to run inside
     * {@code @QuarkusTest} classes ({@code DefaultRegistryStorageTest},
     * {@code KafkaSqlRegistryStorageTest}) where the database is not reset between methods.
     */
    @Test
    public void testSearchArtifactsByLabelWithTurkishLocale() throws Exception {
        Locale savedLocale = Locale.getDefault();
        // Unique suffix prevents collision with any other test's artifacts in the shared DB.
        String uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String artifactId = "testTurkishLocale-" + uniqueSuffix;
        // Uppercase I in the key — this is the character that Turkish locale maps to
        // dotless-i (U+0131) instead of plain 'i', the canonical bug trigger.
        String labelKey = "INSTABILITY-" + uniqueSuffix.toUpperCase();
        try {
            // Switch the JVM default locale to Turkish. After this point any bare
            // .toLowerCase() call will produce the wrong result for keys containing 'I'.
            Locale.setDefault(new Locale("tr", "TR"));

            Map<String, String> labels = Collections.singletonMap(labelKey, "HIGH");
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(
                    "Locale Test Artifact", "locale sensitivity check", null, labels);
            storage().createArtifact(
                    GROUP_ID, artifactId, ArtifactType.OPENAPI, metaData, null,
                    ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON)
                            .content(ContentHandle.create(OPENAPI_CONTENT)).build(),
                    null, Collections.emptyList(), false, false, null).getValue();

            // Search using the same label key. Locale.ROOT normalization on both the
            // write path (storage) and read path (query) must agree, otherwise the
            // stored key ("instability-XXXXX" via ROOT) and the query key
            // ("ınstability-XXXXX" via Turkish) diverge and the result list is empty.
            Set<SearchFilter> filters = Collections.singleton(
                    SearchFilter.ofLabel(labelKey, "HIGH"));
            ArtifactSearchResultsDto results = storage().searchArtifacts(
                    filters, OrderBy.name, OrderDirection.asc, 0, 10, false);

            Assertions.assertNotNull(results);
            // Because the label key contains our unique suffix it cannot match any
            // pre-existing artifact; we assert getCount() >= 1 to allow for any
            // concurrent test activity without making the assertion brittle.
            boolean found = results.getArtifacts().stream()
                    .anyMatch(a -> artifactId.equals(a.getArtifactId()));
            Assertions.assertTrue(found,
                    "Label search must find the artifact even under a Turkish JVM locale. "
                    + "Returned " + results.getCount() + " result(s), none matching "
                    + artifactId + ". This indicates Locale.ROOT was not used consistently "
                    + "in the storage write or search path.");
        } finally {
            // Always restore the JVM locale first so subsequent tests are not affected.
            Locale.setDefault(savedLocale);
            // Then clean up the artifact (locale is already restored at this point).
            try {
                storage().deleteArtifact(GROUP_ID, artifactId);
            } catch (Exception ignored) {
                // Artifact may not exist if createArtifact threw; safe to ignore.
            }
        }
    }

}
