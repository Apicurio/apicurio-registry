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

package io.apicurio.registry.storage.impl.sql;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
class SqlRegistryStorageTest extends AbstractRegistryStorageTest {
    
    @Inject
    SqlRegistryStorage storage;
    @Inject
    TenantContext tenantCtx;
    
    private String tenantId1;
    private String tenantId2;
    
    /**
     * @see io.apicurio.registry.storage.AbstractRegistryStorageTest#storage()
     */
    @Override
    protected RegistryStorage storage() {
        return storage;
    }

    @BeforeEach
    protected void setTenantIds() throws Exception {
        tenantId1 = UUID.randomUUID().toString();
        tenantId2 = UUID.randomUUID().toString();
    }
    
    @AfterEach
    protected void resetTenant() throws Exception {
        tenantCtx.clearTenantId();
    }

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
    
    public void testMultiTenant_ArtifactNotFound() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testArtifactNotFound();
        tenantCtx.tenantId(tenantId2);
        super.testArtifactNotFound();
    }
    
    public void testMultiTenant_CreateArtifactRule() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testCreateArtifactRule();
        tenantCtx.tenantId(tenantId2);
        super.testCreateArtifactRule();
    }
    
    public void testMultiTenant_CreateArtifactVersionWithMetaData() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testCreateArtifactVersionWithMetaData();
        tenantCtx.tenantId(tenantId2);
        super.testCreateArtifactVersionWithMetaData();
    }
    
    public void testMultiTenant_CreateDuplicateArtifact() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testCreateDuplicateArtifact();
        tenantCtx.tenantId(tenantId2);
        super.testCreateDuplicateArtifact();
    }
    
    public void testMultiTenant_UpdateArtifactMetaData() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testUpdateArtifactMetaData();
        tenantCtx.tenantId(tenantId2);
        super.testUpdateArtifactMetaData();
    }
    
    public void testMultiTenant_UpdateArtifactRule() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testUpdateArtifactRule();
        tenantCtx.tenantId(tenantId2);
        super.testUpdateArtifactRule();
    }
    
    public void testMultiTenant_UpdateArtifactState() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testUpdateArtifactState();
        tenantCtx.tenantId(tenantId2);
        super.testUpdateArtifactState();
    }
    
    public void testMultiTenant_UpdateArtifactVersionMetaData() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testUpdateArtifactVersionMetaData();
        tenantCtx.tenantId(tenantId2);
        super.testUpdateArtifactVersionMetaData();
    }
    
    public void testMultiTenant_UpdateArtifactVersionState() throws Exception {
        tenantCtx.tenantId(tenantId1);
        super.testUpdateArtifactVersionState();
        tenantCtx.tenantId(tenantId2);
        super.testUpdateArtifactVersionState();
    }
}
