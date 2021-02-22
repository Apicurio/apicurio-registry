/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.*;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;

/**
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractRegistryStorageTest extends AbstractResourceTestBase {
    
    protected static final String OPENAPI_CONTENT = "{" + 
            "    \"openapi\": \"3.0.2\"," + 
            "    \"info\": {" + 
            "        \"title\": \"Empty API\"," + 
            "        \"version\": \"1.0.0\"," + 
            "        \"description\": \"An example API design using OpenAPI.\"" + 
            "    }" + 
            "}";
    protected static final String OPENAPI_CONTENT_V2 = "{" + 
            "    \"openapi\": \"3.0.2\"," + 
            "    \"info\": {" + 
            "        \"title\": \"Empty API 2\"," + 
            "        \"version\": \"1.0.1\"," + 
            "        \"description\": \"An example API design using OpenAPI.\"" + 
            "    }" + 
            "}";
    protected static final String OPENAPI_CONTENT_TEMPLATE = "{" + 
            "    \"openapi\": \"3.0.2\"," + 
            "    \"info\": {" + 
            "        \"title\": \"Empty API 2\"," + 
            "        \"version\": \"VERSION\"," + 
            "        \"description\": \"An example API design using OpenAPI.\"" + 
            "    }" + 
            "}";

    @Inject
    TenantContext tenantCtx;
    
    String tenantId1;
    String tenantId2;

    @BeforeEach
    protected void setTenantIds() throws Exception {
        tenantId1 = UUID.randomUUID().toString();
        tenantId2 = UUID.randomUUID().toString();
    }
    
    @AfterEach
    protected void resetTenant() throws Exception {
        tenantCtx.clearTenantId();
    }

    /**
     * Gets the storage to use.  Subclasses must provide this.
     */
    protected abstract RegistryStorage storage();

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testGetArtifactIds() throws Exception {
        String artifactIdPrefix = "testGetArtifactIds-";
        for (int idx = 1; idx < 10; idx++) {
            String artifactId = artifactIdPrefix + idx;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
            Assertions.assertNotNull(dto);
            Assertions.assertEquals(artifactId, dto.getId());
        }
        
        Set<String> ids = storage().getArtifactIds(10);
        Assertions.assertNotNull(ids);
        Assertions.assertEquals(10, ids.size());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testCreateArtifact() throws Exception {
        String artifactId = "testCreateArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        StoredArtifact storedArtifact = storage().getArtifact(artifactId);
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(OPENAPI_CONTENT, storedArtifact.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedArtifact.getVersion());
        
        ArtifactMetaDataDto amdDto = storage().getArtifactMetaData(artifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals(dto.getGlobalId(), amdDto.getGlobalId());
        Assertions.assertEquals("Empty API", amdDto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", amdDto.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, amdDto.getState());
        Assertions.assertEquals(1, amdDto.getVersion());
        Assertions.assertNull(amdDto.getLabels());
        Assertions.assertNull(amdDto.getProperties());
        
        ArtifactVersionMetaDataDto versionMetaDataDto = storage().getArtifactVersionMetaData(artifactId, 1);
        Assertions.assertNotNull(versionMetaDataDto);
        Assertions.assertEquals(dto.getGlobalId(), versionMetaDataDto.getGlobalId());
        Assertions.assertEquals("Empty API", versionMetaDataDto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", versionMetaDataDto.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, versionMetaDataDto.getState());
        Assertions.assertEquals(1, versionMetaDataDto.getVersion());
        
        StoredArtifact storedVersion = storage().getArtifactVersion(dto.getGlobalId());
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT, storedVersion.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedVersion.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedVersion.getVersion());
        
        storedVersion = storage().getArtifactVersion(artifactId, 1);
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT, storedVersion.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedVersion.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedVersion.getVersion());
        
        SortedSet<Long> versions = storage().getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertEquals(1, versions.iterator().next());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testCreateArtifactWithMetaData() throws Exception {
        String artifactId = "testCreateArtifactWithMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(
                "NAME", "DESCRIPTION", Collections.singletonList("LABEL-1"), Collections.singletonMap("KEY", "VALUE")
        );
        ArtifactMetaDataDto dto = storage().createArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, content, metaData).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("NAME", dto.getName());
        Assertions.assertEquals("DESCRIPTION", dto.getDescription());
        Assertions.assertNotNull(dto.getLabels());
        Assertions.assertNotNull(dto.getProperties());
        Assertions.assertEquals(metaData.getLabels(), dto.getLabels());
        Assertions.assertEquals(metaData.getProperties(), dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        StoredArtifact storedArtifact = storage().getArtifact(artifactId);
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(OPENAPI_CONTENT, storedArtifact.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedArtifact.getVersion());
        
        ArtifactMetaDataDto amdDto = storage().getArtifactMetaData(artifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals(dto.getGlobalId(), amdDto.getGlobalId());
        Assertions.assertEquals("NAME", amdDto.getName());
        Assertions.assertEquals("DESCRIPTION", amdDto.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, amdDto.getState());
        Assertions.assertEquals(1, amdDto.getVersion());
        Assertions.assertEquals(metaData.getLabels(), amdDto.getLabels());
        Assertions.assertEquals(metaData.getProperties(), amdDto.getProperties());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testCreateDuplicateArtifact() throws Exception {
        String artifactId = "testCreateDuplicateArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        
        // Should throw error for duplicate artifact.
        Assertions.assertThrows(ArtifactAlreadyExistsException.class, () -> {
            storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        });
    }

    @Test
    public void testArtifactNotFound() throws Exception {
        String artifactId = "testArtifactNotFound-1";

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifact(artifactId);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactMetaData(artifactId);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersion(artifactId, 1);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(artifactId, 1);
        });
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testCreateArtifactVersion() throws Exception {
        String artifactId = "testCreateArtifactVersion-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        
        SortedSet<Long> versions = storage().getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());
        
        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactMetaDataDto dtov2 = storage().updateArtifact(artifactId, ArtifactType.OPENAPI, contentv2).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(artifactId, dtov2.getId());
        Assertions.assertEquals(2, dtov2.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, dtov2.getState());

        versions = storage().getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testGetArtifactVersions() throws Exception {
        String artifactId = "testGetArtifactVersions";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        StoredArtifact storedArtifact = storage().getArtifact(artifactId);
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        storedArtifact = storage().getArtifactVersion(artifactId, 1);
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        storedArtifact = storage().getArtifactVersion(dto.getGlobalId());
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        ArtifactMetaDataDto dtov1 = storage().getArtifactMetaData(dto.getGlobalId());
        verifyArtifactMetadata(dtov1, dto);

        SortedSet<Long> versions = storage().getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactMetaDataDto dtov2 = storage().updateArtifact(artifactId, ArtifactType.OPENAPI, contentv2).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(artifactId, dtov2.getId());
        Assertions.assertEquals(2, dtov2.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, dtov2.getState());

        versions = storage().getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());

        //verify version 2

        storedArtifact = storage().getArtifact(artifactId);
        verifyArtifact(storedArtifact, OPENAPI_CONTENT_V2, dtov2);

        storedArtifact = storage().getArtifactVersion(artifactId, 2);
        verifyArtifact(storedArtifact, OPENAPI_CONTENT_V2, dtov2);

        storedArtifact = storage().getArtifactVersion(dtov2.getGlobalId());
        verifyArtifact(storedArtifact, OPENAPI_CONTENT_V2, dtov2);

        ArtifactMetaDataDto dtov2Stored = storage().getArtifactMetaData(dtov2.getGlobalId());
        verifyArtifactMetadata(dtov2Stored, dtov2);

        // verify version 1 again

        storedArtifact = storage().getArtifactVersion(artifactId, 1);
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        storedArtifact = storage().getArtifactVersion(dto.getGlobalId());
        verifyArtifact(storedArtifact, OPENAPI_CONTENT, dto);

        dtov1 = storage().getArtifactMetaData(dto.getGlobalId());
        verifyArtifactMetadata(dtov1, dto);

    }

    private void verifyArtifact(StoredArtifact storedArtifact, String content, ArtifactMetaDataDto expectedMetadata) {
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(content, storedArtifact.getContent().content());
        Assertions.assertEquals(expectedMetadata.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(expectedMetadata.getVersion(), storedArtifact.getVersion());
    }

    private void verifyArtifactMetadata(ArtifactMetaDataDto actualMetadata, ArtifactMetaDataDto expectedMetadata) {
        Assertions.assertNotNull(actualMetadata);
        Assertions.assertNotNull(expectedMetadata);
        Assertions.assertEquals(expectedMetadata.getGlobalId(), actualMetadata.getGlobalId());
        Assertions.assertEquals(expectedMetadata.getVersion(), actualMetadata.getVersion());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testCreateArtifactVersionWithMetaData() throws Exception {
        String artifactId = "testCreateArtifactVersionWithMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        
        SortedSet<Long> versions = storage().getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());
        
        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto("NAME", "DESC", Collections.singletonList("LBL"), Collections.singletonMap("K", "V"));
        ArtifactMetaDataDto dtov2 = storage().updateArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, contentv2, metaData).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(artifactId, dtov2.getId());
        Assertions.assertEquals(2, dtov2.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, dtov2.getState());
        Assertions.assertEquals("NAME", dtov2.getName());
        Assertions.assertEquals("DESC", dtov2.getDescription());
        Assertions.assertEquals(metaData.getLabels(), dtov2.getLabels());
        Assertions.assertEquals(metaData.getProperties(), dtov2.getProperties());

        versions = storage().getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());

        ArtifactVersionMetaDataDto vmd = storage().getArtifactVersionMetaData(artifactId, 2);
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals("NAME", vmd.getName());
        Assertions.assertEquals("DESC", vmd.getDescription());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testGetArtifactMetaDataByGlobalId() throws Exception {
        String artifactId = "testGetArtifactMetaDataByGlobalId-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        long globalId = dto.getGlobalId();
        
        dto = storage().getArtifactMetaData(globalId);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testUpdateArtifactMetaData() throws Exception {
        String artifactId = "testUpdateArtifactMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        String newName = "Updated Name";
        String newDescription = "Updated description.";
        List<String> newLabels = Collections.singletonList("foo");
        Map<String, String> newProperties = new HashMap<>();
        newProperties.put("foo", "bar");
        newProperties.put("ting", "bin");
        EditableArtifactMetaDataDto emd = new EditableArtifactMetaDataDto(newName, newDescription, newLabels, newProperties);
        storage().updateArtifactMetaData(artifactId, emd);
        
        ArtifactMetaDataDto metaData = storage().getArtifactMetaData(artifactId);
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(newName, metaData.getName());
        Assertions.assertEquals(newDescription, metaData.getDescription());
        Assertions.assertEquals(newLabels, metaData.getLabels());
        Assertions.assertEquals(newProperties, metaData.getProperties());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testUpdateArtifactState() throws Exception {
        String artifactId = "testUpdateArtifactState-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        
        storage().updateArtifactState(artifactId, ArtifactState.DEPRECATED);
        
        ArtifactMetaDataDto metaData = storage().getArtifactMetaData(artifactId);
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(ArtifactState.DEPRECATED, metaData.getState());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testUpdateArtifactVersionState() throws Exception {
        String artifactId = "testUpdateArtifactVersionState-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactMetaDataDto dtov2 = storage().updateArtifact(artifactId, ArtifactType.OPENAPI, contentv2).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(artifactId, dtov2.getId());
        Assertions.assertEquals(2, dtov2.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, dtov2.getState());
        
        storage().updateArtifactState(artifactId, ArtifactState.DISABLED, 1);
        storage().updateArtifactState(artifactId, ArtifactState.DEPRECATED, 2);
        
        ArtifactVersionMetaDataDto v1 = storage().getArtifactVersionMetaData(artifactId, 1);
        ArtifactVersionMetaDataDto v2 = storage().getArtifactVersionMetaData(artifactId, 2);
        Assertions.assertNotNull(v1);
        Assertions.assertNotNull(v2);
        Assertions.assertEquals(ArtifactState.DISABLED, v1.getState());
        Assertions.assertEquals(ArtifactState.DEPRECATED, v2.getState());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testUpdateArtifactVersionMetaData() throws Exception {
        String artifactId = "testUpdateArtifactVersionMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        String newName = "Updated Name";
        String newDescription = "Updated description.";
        List<String> newLabels = Collections.singletonList("foo");
        Map<String, String> newProperties = new HashMap<>();
        newProperties.put("foo", "bar");
        newProperties.put("ting", "bin");
        EditableArtifactMetaDataDto emd = new EditableArtifactMetaDataDto(newName, newDescription, newLabels, newProperties);
        storage().updateArtifactVersionMetaData(artifactId, 1, emd);
        
        ArtifactVersionMetaDataDto metaData = storage().getArtifactVersionMetaData(artifactId, 1);
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(newName, metaData.getName());
        Assertions.assertEquals(newDescription, metaData.getDescription());
    }

    @Test
    public void testDeleteArtifact() throws Exception {
        String artifactId = "testDeleteArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        storage().getArtifact(artifactId);
        
        storage().deleteArtifact(artifactId);
        
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifact(artifactId);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactMetaData(artifactId);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersion(artifactId, 1);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(artifactId, 1);
        });
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testDeleteArtifactVersion() throws Exception {
        // Delete the only version
        ////////////////////////////
        String artifactId = "testDeleteArtifactVersion-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        storage().deleteArtifactVersion(artifactId, 1);

        final String aid1 = artifactId;
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifact(aid1);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactMetaData(aid1);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersion(aid1, 1);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(aid1, 1);
        });
        
        // Delete one of multiple versions
        artifactId = "testDeleteArtifactVersion-2";
        content = ContentHandle.create(OPENAPI_CONTENT);
        dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());

        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactMetaDataDto dtov2 = storage().updateArtifact(artifactId, ArtifactType.OPENAPI, contentv2).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(2, dtov2.getVersion());
        
        storage().deleteArtifactVersion(artifactId, 1);
        
        final String aid2 = artifactId;
        
        storage().getArtifact(aid2);
        storage().getArtifactMetaData(aid2);
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersion(aid2, 1);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(aid2, 1);
        });
        
        // Delete the latest version
        artifactId = "testDeleteArtifactVersion-3";
        content = ContentHandle.create(OPENAPI_CONTENT);
        dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());

        contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        dtov2 = storage().updateArtifact(artifactId, ArtifactType.OPENAPI, contentv2).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        
        final String aid3 = artifactId;
        storage().deleteArtifactVersion(aid3, 2);
        SortedSet<Long> versions = storage().getArtifactVersions(aid3);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());
        Assertions.assertEquals(1, versions.first());
        
        VersionSearchResults result = storage().searchVersions(aid3, 0, 10);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getCount());
        Assertions.assertEquals(1, result.getVersions().iterator().next().getVersion());
        
        ArtifactMetaDataDto artifactMetaData = storage().getArtifactMetaData(aid3);
        Assertions.assertNotNull(artifactMetaData);
        Assertions.assertEquals(1, artifactMetaData.getVersion());
        Assertions.assertEquals(aid3, artifactMetaData.getId());
        
        storage().getArtifact(aid3);
        ArtifactMetaDataDto metaData = storage().getArtifactMetaData(aid3);
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(1, metaData.getVersion());
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersion(aid3, 2);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage().getArtifactVersionMetaData(aid3, 2);
        });
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testDeleteArtifactVersionMetaData() throws Exception {
        String artifactId = "testDeleteArtifactVersionMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        storage().deleteArtifactVersionMetaData(artifactId, 1);
        
        ArtifactVersionMetaDataDto metaData = storage().getArtifactVersionMetaData(artifactId, 1);
        Assertions.assertNotNull(metaData);
        Assertions.assertNull(metaData.getName());
        Assertions.assertNull(metaData.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, metaData.getState());
        Assertions.assertEquals(1, metaData.getVersion());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testCreateArtifactRule() throws Exception {
        String artifactId = "testCreateArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        List<RuleType> artifactRules = storage().getArtifactRules(artifactId);
        Assertions.assertNotNull(artifactRules);
        Assertions.assertTrue(artifactRules.isEmpty());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(artifactId, RuleType.VALIDITY, configDto);

        artifactRules = storage().getArtifactRules(artifactId);
        Assertions.assertNotNull(artifactRules);
        Assertions.assertFalse(artifactRules.isEmpty());
        Assertions.assertEquals(1, artifactRules.size());
        Assertions.assertEquals(RuleType.VALIDITY, artifactRules.get(0));
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testUpdateArtifactRule() throws Exception {
        String artifactId = "testUpdateArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(artifactId, RuleType.VALIDITY, configDto);

        RuleConfigurationDto rule = storage().getArtifactRule(artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("FULL", rule.getConfiguration());
        
        RuleConfigurationDto updatedConfig = new RuleConfigurationDto("NONE");
        storage().updateArtifactRule(artifactId, RuleType.VALIDITY, updatedConfig);

        rule = storage().getArtifactRule(artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("NONE", rule.getConfiguration());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testDeleteArtifactRule() throws Exception {
        String artifactId = "testDeleteArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(artifactId, RuleType.VALIDITY, configDto);

        RuleConfigurationDto rule = storage().getArtifactRule(artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("FULL", rule.getConfiguration());
        
        storage().deleteArtifactRule(artifactId, RuleType.VALIDITY);

        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage().getArtifactRule(artifactId, RuleType.VALIDITY);
        });
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testDeleteAllArtifactRules() throws Exception {
        String artifactId = "testDeleteAllArtifactRulse-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage().createArtifactRule(artifactId, RuleType.VALIDITY, configDto);
        storage().createArtifactRule(artifactId, RuleType.COMPATIBILITY, configDto);

        List<RuleType> rules = storage().getArtifactRules(artifactId);
        Assertions.assertEquals(2, rules.size());
        
        storage().deleteArtifactRules(artifactId);

        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage().getArtifactRule(artifactId, RuleType.VALIDITY);
        });
        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage().getArtifactRule(artifactId, RuleType.COMPATIBILITY);
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

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testSearchArtifacts() throws Exception {
        String artifactIdPrefix = "testSearchArtifacts-";
        for (int idx = 1; idx <= 50; idx++) {
            String artifactId = artifactIdPrefix + idx;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            List<String> labels = Collections.singletonList("label-" + idx);
            Map<String, String> properties = Collections.singletonMap("key", "value-" + idx);
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(
                    artifactId + "-name",
                    artifactId + "-description",
                    labels,
                    properties);
            storage().createArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, content, metaData).toCompletableFuture().get();
        }
        
        long start = System.currentTimeMillis();
        
        ArtifactSearchResults results = storage().searchArtifacts("testSearchArtifacts", 0, 10, SearchOver.name, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(50, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(10, results.getArtifacts().size());
        
        results = storage().searchArtifacts("testSearchArtifacts-19-name", 0, 10, SearchOver.name, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-19-name", results.getArtifacts().get(0).getName());
        
        results = storage().searchArtifacts("testSearchArtifacts-33-description", 0, 10, SearchOver.description, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-33-name", results.getArtifacts().get(0).getName());
        
        results = storage().searchArtifacts(null, 0, 11, SearchOver.everything, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(11, results.getArtifacts().size());
        
        results = storage().searchArtifacts("testSearchArtifacts", 0, 1000, SearchOver.everything, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(50, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(50, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-1-name", results.getArtifacts().get(0).getName());
        Assertions.assertEquals("testSearchArtifacts-10-name", results.getArtifacts().get(1).getName());

        results = storage().searchArtifacts("label-17", 0, 10, SearchOver.labels, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-17-name", results.getArtifacts().get(0).getName());

        results = storage().searchArtifacts("label-17", 0, 10, SearchOver.everything, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-17-name", results.getArtifacts().get(0).getName());

        long end = System.currentTimeMillis();
        System.out.println("Search time: " + (end - start) + "ms");
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testSearchVersions() throws Exception {
        String artifactId = "testSearchVersions-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        
        // Add more versions
        for (int idx = 2; idx <= 50; idx++) {
            content = ContentHandle.create(OPENAPI_CONTENT_TEMPLATE.replaceAll("VERSION", "1.0." + idx));
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(
                    artifactId + "-name-" + idx,
                    artifactId + "-description-" + idx,
                    null,
                    null);
            storage().updateArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, content, metaData);
        }

        TestUtils.retry(() -> {
            VersionSearchResults results = storage().searchVersions(artifactId, 0, 10);
            Assertions.assertNotNull(results);
            Assertions.assertEquals(50, results.getCount());
            Assertions.assertEquals(10, results.getVersions().size());
    
            results = storage().searchVersions(artifactId, 0, 1000);
            Assertions.assertNotNull(results);
            Assertions.assertEquals(50, results.getCount());
            Assertions.assertEquals(50, results.getVersions().size());
        });
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testMultiTenant_CreateArtifact() throws Exception {
        // Add an artifact for tenantId 1
        tenantCtx.tenantId(tenantId1);
        String artifactId = "testMultiTenant_CreateArtifact";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        StoredArtifact storedArtifact = storage().getArtifact(artifactId);
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(OPENAPI_CONTENT, storedArtifact.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedArtifact.getVersion());
        
        ArtifactMetaDataDto amdDto = storage().getArtifactMetaData(artifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals(dto.getGlobalId(), amdDto.getGlobalId());
        Assertions.assertEquals("Empty API", amdDto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", amdDto.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, amdDto.getState());
        Assertions.assertEquals(1, amdDto.getVersion());
        Assertions.assertNull(amdDto.getLabels());
        Assertions.assertNull(amdDto.getProperties());
        
        // Switch to tenantId 2 and make sure the GET operations no longer work
        tenantCtx.tenantId(tenantId2);
        try {
            storage().getArtifact(artifactId);
            Assertions.fail("Expected 404 not found for TENANT-2");
        } catch (ArtifactNotFoundException e) {
            // correct response
        }
    }

    @Test
    public void testMultiTenant_CreateSameArtifact() throws Exception {
        // Add an artifact for tenantId 1
        tenantCtx.tenantId(tenantId1);
        String artifactId = "testMultiTenant_CreateSameArtifact";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        // Switch to tenantId 2 and create the same artifact
        tenantCtx.tenantId(tenantId2);
        content = ContentHandle.create(OPENAPI_CONTENT);
        dto = storage().createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
    }

    @Test
    public void testMultiTenant_Search() throws Exception {
        // Add 10 artifacts for tenantId 1
        tenantCtx.tenantId(tenantId1);
        String artifactIdPrefix = "testMultiTenant_Search-";
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = artifactIdPrefix + idx;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            List<String> labels = Collections.singletonList("testMultiTenant_Search");
            Map<String, String> properties = Collections.emptyMap();
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(
                    artifactId + "-name",
                    artifactId + "-description",
                    labels,
                    properties);
            storage().createArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, content, metaData).toCompletableFuture().get();
        }
        
        // Search for the artifacts using Tenant 2 (0 results expected)
        tenantCtx.tenantId(tenantId2);
        ArtifactSearchResults searchResults = storage().searchArtifacts("testMultiTenant_Search", 0, 100, SearchOver.labels, SortOrder.asc);
        Assertions.assertNotNull(searchResults);
        Assertions.assertEquals(0, searchResults.getCount());
        Assertions.assertTrue(searchResults.getArtifacts().isEmpty());
        
        // Search for the artifacts using Tenant 1 (10 results expected)
        tenantCtx.tenantId(tenantId1);
        searchResults = storage().searchArtifacts("testMultiTenant_Search", 0, 100, SearchOver.labels, SortOrder.asc);
        Assertions.assertNotNull(searchResults);
        Assertions.assertEquals(10, searchResults.getCount());
        Assertions.assertEquals(10, searchResults.getArtifacts().size());
    }

    @Test
    public void testMultiTenant_GlobalRules() {
        tenantCtx.tenantId(tenantId1);

        List<RuleType> globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());
        
        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration("FULL");
        storage().createGlobalRule(RuleType.COMPATIBILITY, config);
        try {
            storage().createGlobalRule(RuleType.COMPATIBILITY, config);
            Assertions.fail("Expected a RuleAlreadyExistsException for " + RuleType.COMPATIBILITY);
        } catch (RuleAlreadyExistsException e) {
            // this is expected
        }
        
        RuleConfigurationDto rule = storage().getGlobalRule(RuleType.COMPATIBILITY);
        Assertions.assertEquals(rule.getConfiguration(), config.getConfiguration());
        
        globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertFalse(globalRules.isEmpty());
        Assertions.assertEquals(globalRules.size(), 1);
        Assertions.assertEquals(globalRules.get(0), RuleType.COMPATIBILITY);
        
        ///////////////////////////////////////////////////
        // Now switch to tenant #2 and do the same stuff
        ///////////////////////////////////////////////////
        
        tenantCtx.tenantId(tenantId2);
        globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());
        
        config = new RuleConfigurationDto();
        config.setConfiguration("FULL");
        storage().createGlobalRule(RuleType.COMPATIBILITY, config);
        try {
            storage().createGlobalRule(RuleType.COMPATIBILITY, config);
            Assertions.fail("Expected a RuleAlreadyExistsException for " + RuleType.COMPATIBILITY);
        } catch (RuleAlreadyExistsException e) {
            // this is expected
        }
        
        rule = storage().getGlobalRule(RuleType.COMPATIBILITY);
        Assertions.assertEquals(rule.getConfiguration(), config.getConfiguration());
        
        globalRules = storage().getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertFalse(globalRules.isEmpty());
        Assertions.assertEquals(globalRules.size(), 1);
        Assertions.assertEquals(globalRules.get(0), RuleType.COMPATIBILITY);
    }
    
    @Test
    public void testMultiTenant_ArtifactNotFound() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testArtifactNotFound();
        tenantCtx.tenantId(tenantId2);
        this.testArtifactNotFound();
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testMultiTenant_CreateArtifactRule() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testCreateArtifactRule();
        tenantCtx.tenantId(tenantId2);
        this.testCreateArtifactRule();
    }
    
    @Test
    public void testMultiTenant_CreateArtifactVersionWithMetaData() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testCreateArtifactVersionWithMetaData();
        tenantCtx.tenantId(tenantId2);
        this.testCreateArtifactVersionWithMetaData();
    }
    
    @Test
    public void testMultiTenant_CreateDuplicateArtifact() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testCreateDuplicateArtifact();
        tenantCtx.tenantId(tenantId2);
        this.testCreateDuplicateArtifact();
    }
    
    @Test
    public void testMultiTenant_UpdateArtifactMetaData() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testUpdateArtifactMetaData();
        tenantCtx.tenantId(tenantId2);
        this.testUpdateArtifactMetaData();
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testMultiTenant_UpdateArtifactRule() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testUpdateArtifactRule();
        tenantCtx.tenantId(tenantId2);
        this.testUpdateArtifactRule();
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testMultiTenant_UpdateArtifactState() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testUpdateArtifactState();
        tenantCtx.tenantId(tenantId2);
        this.testUpdateArtifactState();
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testMultiTenant_UpdateArtifactVersionMetaData() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testUpdateArtifactVersionMetaData();
        tenantCtx.tenantId(tenantId2);
        this.testUpdateArtifactVersionMetaData();
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testMultiTenant_UpdateArtifactVersionState() throws Exception {
        tenantCtx.tenantId(tenantId1);
        this.testUpdateArtifactVersionState();
        tenantCtx.tenantId(tenantId2);
        this.testUpdateArtifactVersionState();
    }

}
