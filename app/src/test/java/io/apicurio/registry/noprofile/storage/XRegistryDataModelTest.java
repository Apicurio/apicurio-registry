package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.DeprecationInfoDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableResourceMetaDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.ResourceMetaDto;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@QuarkusTest
public class XRegistryDataModelTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = "XRegistryDataModelTest";

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void testGroupEpochStartsAtZero() throws Exception {
        String groupId = GROUP_ID + ".testGroupEpochStartsAtZero";

        GroupMetaDataDto group = GroupMetaDataDto.builder()
                .groupId(groupId)
                .description("Test group")
                .owner("test-owner")
                .build();
        storage.createGroup(group);

        GroupMetaDataDto result = storage.getGroupMetaData(groupId);
        Assertions.assertEquals(0, result.getEpoch());

        storage.deleteGroup(groupId);
    }

    @Test
    public void testGroupEpochIncrementsOnUpdate() throws Exception {
        String groupId = GROUP_ID + ".testGroupEpochIncrementsOnUpdate";

        GroupMetaDataDto group = GroupMetaDataDto.builder()
                .groupId(groupId)
                .description("Test group")
                .owner("test-owner")
                .build();
        storage.createGroup(group);

        GroupMetaDataDto result = storage.getGroupMetaData(groupId);
        Assertions.assertEquals(0, result.getEpoch());

        storage.updateGroupMetaData(groupId,
                EditableGroupMetaDataDto.builder().description("Updated description").build());

        result = storage.getGroupMetaData(groupId);
        Assertions.assertEquals(1, result.getEpoch());

        storage.updateGroupMetaData(groupId,
                EditableGroupMetaDataDto.builder().description("Updated again").build());

        result = storage.getGroupMetaData(groupId);
        Assertions.assertEquals(2, result.getEpoch());

        storage.deleteGroup(groupId);
    }

    @Test
    public void testArtifactEpochStartsAtZero() throws Exception {
        String groupId = GROUP_ID + ".testArtifactEpochStartsAtZero";
        String artifactId = "test-artifact-epoch";

        storage.createGroup(GroupMetaDataDto.builder().groupId(groupId).owner("test").build());

        var amd = EditableArtifactMetaDataDto.builder()
                .name("test").description("test artifact").build();
        var vmd = EditableVersionMetaDataDto.builder().build();
        storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, amd, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create("{\"openapi\":\"3.0.0\"}")).build(),
                vmd, Collections.emptyList(), false, false, null);

        ArtifactMetaDataDto result = storage.getArtifactMetaData(groupId, artifactId);
        Assertions.assertEquals(0, result.getEpoch());

        storage.deleteGroup(groupId);
    }

    @Test
    public void testArtifactEpochIncrementsOnUpdate() throws Exception {
        String groupId = GROUP_ID + ".testArtifactEpochIncrementsOnUpdate";
        String artifactId = "test-artifact-epoch-update";

        storage.createGroup(GroupMetaDataDto.builder().groupId(groupId).owner("test").build());

        var amd = EditableArtifactMetaDataDto.builder()
                .name("test").description("test artifact").build();
        var vmd = EditableVersionMetaDataDto.builder().build();
        storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, amd, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create("{\"openapi\":\"3.0.0\"}")).build(),
                vmd, Collections.emptyList(), false, false, null);

        Assertions.assertEquals(0, storage.getArtifactMetaData(groupId, artifactId).getEpoch());

        storage.updateArtifactMetaData(groupId, artifactId,
                EditableArtifactMetaDataDto.builder().name("Updated name").build());

        Assertions.assertEquals(1, storage.getArtifactMetaData(groupId, artifactId).getEpoch());

        storage.deleteGroup(groupId);
    }

    @Test
    public void testVersionEpochStartsAtZero() throws Exception {
        String groupId = GROUP_ID + ".testVersionEpochStartsAtZero";
        String artifactId = "test-version-epoch";

        storage.createGroup(GroupMetaDataDto.builder().groupId(groupId).owner("test").build());

        var amd = EditableArtifactMetaDataDto.builder().name("test").build();
        var vmd = EditableVersionMetaDataDto.builder().build();
        storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, amd, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create("{\"openapi\":\"3.0.0\"}")).build(),
                vmd, Collections.emptyList(), false, false, null);

        ArtifactVersionMetaDataDto result = storage.getArtifactVersionMetaData(groupId, artifactId, "1");
        Assertions.assertEquals(0, result.getEpoch());

        storage.deleteGroup(groupId);
    }

    @Test
    public void testResourceMetaCrud() throws Exception {
        String groupId = GROUP_ID + ".testResourceMetaCrud";
        String artifactId = "test-resource-meta";

        storage.createGroup(GroupMetaDataDto.builder().groupId(groupId).owner("test").build());

        var amd = EditableArtifactMetaDataDto.builder().name("test").build();
        var vmd = EditableVersionMetaDataDto.builder().build();
        storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, amd, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create("{\"openapi\":\"3.0.0\"}")).build(),
                vmd, Collections.emptyList(), false, false, null);

        ResourceMetaDto meta = storage.getResourceMeta(groupId, artifactId);
        Assertions.assertNotNull(meta);
        Assertions.assertEquals(groupId, meta.getGroupId());
        Assertions.assertEquals(artifactId, meta.getArtifactId());

        EditableResourceMetaDto update = EditableResourceMetaDto.builder()
                .compatibility("backward")
                .compatibilityAuthority("server")
                .defaultVersionSticky(true)
                .readonly(false)
                .build();
        storage.updateResourceMeta(groupId, artifactId, update);

        meta = storage.getResourceMeta(groupId, artifactId);
        Assertions.assertEquals("backward", meta.getCompatibility());
        Assertions.assertEquals("server", meta.getCompatibilityAuthority());
        Assertions.assertEquals(Boolean.TRUE, meta.getDefaultVersionSticky());
        Assertions.assertEquals(Boolean.FALSE, meta.getReadonly());

        storage.deleteResourceMeta(groupId, artifactId);

        meta = storage.getResourceMeta(groupId, artifactId);
        Assertions.assertNull(meta.getCompatibility());

        storage.deleteGroup(groupId);
    }

    @Test
    public void testResourceMetaDeprecation() throws Exception {
        String groupId = GROUP_ID + ".testResourceMetaDeprecation";
        String artifactId = "test-deprecation";

        storage.createGroup(GroupMetaDataDto.builder().groupId(groupId).owner("test").build());

        var amd = EditableArtifactMetaDataDto.builder().name("test").build();
        var vmd = EditableVersionMetaDataDto.builder().build();
        storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, amd, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create("{\"openapi\":\"3.0.0\"}")).build(),
                vmd, Collections.emptyList(), false, false, null);

        long effectiveTime = System.currentTimeMillis();
        long removalTime = effectiveTime + 86400000L;

        EditableResourceMetaDto update = EditableResourceMetaDto.builder()
                .deprecated(DeprecationInfoDto.builder()
                        .effective(effectiveTime)
                        .removal(removalTime)
                        .alternative("some-other-artifact")
                        .documentation("https://example.com/migration")
                        .build())
                .build();
        storage.updateResourceMeta(groupId, artifactId, update);

        ResourceMetaDto meta = storage.getResourceMeta(groupId, artifactId);
        Assertions.assertNotNull(meta.getDeprecated());
        Assertions.assertEquals(effectiveTime, meta.getDeprecated().getEffective());
        Assertions.assertEquals(removalTime, meta.getDeprecated().getRemoval());
        Assertions.assertEquals("some-other-artifact", meta.getDeprecated().getAlternative());
        Assertions.assertEquals("https://example.com/migration", meta.getDeprecated().getDocumentation());

        storage.deleteGroup(groupId);
    }
}
