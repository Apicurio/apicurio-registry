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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
class SqlRegistryStorageTest {
    
    private static final String OPENAPI_CONTENT = "{" + 
            "    \"openapi\": \"3.0.2\"," + 
            "    \"info\": {" + 
            "        \"title\": \"Empty API\"," + 
            "        \"version\": \"1.0.0\"," + 
            "        \"description\": \"An example API design using OpenAPI.\"" + 
            "    }" + 
            "}";
    private static final String OPENAPI_CONTENT_V2 = "{" + 
            "    \"openapi\": \"3.0.2\"," + 
            "    \"info\": {" + 
            "        \"title\": \"Empty API 2\"," + 
            "        \"version\": \"1.0.1\"," + 
            "        \"description\": \"An example API design using OpenAPI.\"" + 
            "    }" + 
            "}";

    @Inject
    SqlRegistryStorage storage;
    

    @Test
    void testGetArtifactIds() throws Exception {
        String artifactIdPrefix = "testGetArtifactIds-";
        for (int idx = 1; idx < 10; idx++) {
            String artifactId = artifactIdPrefix + idx;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
            Assertions.assertNotNull(dto);
            Assertions.assertEquals(artifactId, dto.getId());
        }
        
        Set<String> ids = storage.getArtifactIds(10);
        Assertions.assertNotNull(ids);
        Assertions.assertEquals(10, ids.size());
    }
    
    @Test
    void testCreateArtifact() throws Exception {
        String artifactId = "testCreateArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        StoredArtifact storedArtifact = this.storage.getArtifact(artifactId);
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(OPENAPI_CONTENT, storedArtifact.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedArtifact.getVersion());
        
        ArtifactMetaDataDto amdDto = this.storage.getArtifactMetaData(artifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals(dto.getGlobalId(), amdDto.getGlobalId());
        Assertions.assertEquals("Empty API", amdDto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", amdDto.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, amdDto.getState());
        Assertions.assertEquals(1, amdDto.getVersion());
        Assertions.assertNull(amdDto.getLabels());
        Assertions.assertNull(amdDto.getProperties());
        
        ArtifactVersionMetaDataDto versionMetaDataDto = this.storage.getArtifactVersionMetaData(artifactId, 1);
        Assertions.assertNotNull(versionMetaDataDto);
        Assertions.assertEquals(dto.getGlobalId(), versionMetaDataDto.getGlobalId());
        Assertions.assertEquals("Empty API", versionMetaDataDto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", versionMetaDataDto.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, versionMetaDataDto.getState());
        Assertions.assertEquals(1, versionMetaDataDto.getVersion());
        
        StoredArtifact storedVersion = this.storage.getArtifactVersion(dto.getGlobalId());
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT, storedVersion.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedVersion.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedVersion.getVersion());
        
        storedVersion = this.storage.getArtifactVersion(artifactId, 1);
        Assertions.assertNotNull(storedVersion);
        Assertions.assertEquals(OPENAPI_CONTENT, storedVersion.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedVersion.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedVersion.getVersion());
        
        SortedSet<Long> versions = this.storage.getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertEquals(1, versions.iterator().next());
    }

    @Test
    void testCreateArtifactWithMetaData() throws Exception {
        String artifactId = "testCreateArtifactWithMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(
                "NAME", "DESCRIPTION", Collections.singletonList("LABEL-1"), Collections.singletonMap("KEY", "VALUE")
        );
        ArtifactMetaDataDto dto = this.storage.createArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, content, metaData).toCompletableFuture().get();
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
        
        StoredArtifact storedArtifact = this.storage.getArtifact(artifactId);
        Assertions.assertNotNull(storedArtifact);
        Assertions.assertEquals(OPENAPI_CONTENT, storedArtifact.getContent().content());
        Assertions.assertEquals(dto.getGlobalId(), storedArtifact.getGlobalId());
        Assertions.assertEquals(dto.getVersion(), storedArtifact.getVersion());
        
        ArtifactMetaDataDto amdDto = this.storage.getArtifactMetaData(artifactId);
        Assertions.assertNotNull(amdDto);
        Assertions.assertEquals(dto.getGlobalId(), amdDto.getGlobalId());
        Assertions.assertEquals("NAME", amdDto.getName());
        Assertions.assertEquals("DESCRIPTION", amdDto.getDescription());
        Assertions.assertEquals(ArtifactState.ENABLED, amdDto.getState());
        Assertions.assertEquals(1, amdDto.getVersion());
        Assertions.assertEquals(metaData.getLabels(), amdDto.getLabels());
        Assertions.assertEquals(metaData.getProperties(), amdDto.getProperties());
    }

    @Test
    void testCreateDuplicateArtifact() throws Exception {
        String artifactId = "testCreateDuplicateArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        
        // Should throw error for duplicate artifact.
        Assertions.assertThrows(ArtifactAlreadyExistsException.class, () -> {
            this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        });
    }

    @Test
    void testArtifactNotFound() throws Exception {
        String artifactId = "testArtifactNotFound-1";

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            this.storage.getArtifact(artifactId);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            this.storage.getArtifactMetaData(artifactId);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            this.storage.getArtifactVersion(artifactId, 1);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            this.storage.getArtifactVersionMetaData(artifactId, 1);
        });
    }

    @Test
    void testCreateArtifactVersion() throws Exception {
        String artifactId = "testCreateArtifactVersion-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        
        SortedSet<Long> versions = this.storage.getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());
        
        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        ArtifactMetaDataDto dtov2 = this.storage.updateArtifact(artifactId, ArtifactType.OPENAPI, contentv2).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(artifactId, dtov2.getId());
        Assertions.assertEquals(2, dtov2.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, dtov2.getState());

        versions = this.storage.getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());
    }

    @Test
    void testCreateArtifactVersionWithMetaData() throws Exception {
        String artifactId = "testCreateArtifactVersionWithMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        
        SortedSet<Long> versions = this.storage.getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(1, versions.size());
        
        ContentHandle contentv2 = ContentHandle.create(OPENAPI_CONTENT_V2);
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto("NAME", "DESC", Collections.singletonList("LBL"), Collections.singletonMap("K", "V"));
        ArtifactMetaDataDto dtov2 = this.storage.updateArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, contentv2, metaData ).toCompletableFuture().get();
        Assertions.assertNotNull(dtov2);
        Assertions.assertEquals(artifactId, dtov2.getId());
        Assertions.assertEquals(2, dtov2.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, dtov2.getState());
        Assertions.assertEquals("NAME", dtov2.getName());
        Assertions.assertEquals("DESC", dtov2.getDescription());
        Assertions.assertEquals(metaData.getLabels(), dtov2.getLabels());
        Assertions.assertEquals(metaData.getProperties(), dtov2.getProperties());

        versions = this.storage.getArtifactVersions(artifactId);
        Assertions.assertNotNull(versions);
        Assertions.assertFalse(versions.isEmpty());
        Assertions.assertEquals(2, versions.size());

        ArtifactVersionMetaDataDto vmd = this.storage.getArtifactVersionMetaData(artifactId, 2);
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals("NAME", vmd.getName());
        Assertions.assertEquals("DESC", vmd.getDescription());
    }

    @Test
    void testGetArtifactMetaDataByGlobalId() throws Exception {
        String artifactId = "testGetArtifactMetaDataByGlobalId-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        long globalId = dto.getGlobalId();
        
        dto = storage.getArtifactMetaData(globalId);
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
    void testUpdateArtifactMetaData() throws Exception {
        String artifactId = "testUpdateArtifactMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
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
        this.storage.updateArtifactMetaData(artifactId, emd);
        
        ArtifactMetaDataDto metaData = this.storage.getArtifactMetaData(artifactId);
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(newName, metaData.getName());
        Assertions.assertEquals(newDescription, metaData.getDescription());
        Assertions.assertEquals(newLabels, metaData.getLabels());
        Assertions.assertEquals(newProperties, metaData.getProperties());
    }

    @Test
    void testUpdateArtifactVersionMetaData() throws Exception {
        String artifactId = "testUpdateArtifactVersionMetaData-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
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
        this.storage.updateArtifactVersionMetaData(artifactId, 1, emd);
        
        ArtifactVersionMetaDataDto metaData = this.storage.getArtifactVersionMetaData(artifactId, 1);
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(newName, metaData.getName());
        Assertions.assertEquals(newDescription, metaData.getDescription());
    }

    @Test
    void testDeleteArtifact() throws Exception {
        String artifactId = "testDeleteArtifact-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());
        Assertions.assertEquals("Empty API", dto.getName());
        Assertions.assertEquals("An example API design using OpenAPI.", dto.getDescription());
        Assertions.assertNull(dto.getLabels());
        Assertions.assertNull(dto.getProperties());
        Assertions.assertEquals(ArtifactState.ENABLED, dto.getState());
        Assertions.assertEquals(1, dto.getVersion());
        
        storage.getArtifact(artifactId);
        
        storage.deleteArtifact(artifactId);
        
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage.getArtifact(artifactId);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage.getArtifactMetaData(artifactId);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage.getArtifactVersion(artifactId, 1);
        });
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            storage.getArtifactVersionMetaData(artifactId, 1);
        });
    }
    
    @Test
    void testCreateArtifactRule() throws Exception {
        String artifactId = "testCreateArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        List<RuleType> artifactRules = storage.getArtifactRules(artifactId);
        Assertions.assertNotNull(artifactRules);
        Assertions.assertTrue(artifactRules.isEmpty());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage.createArtifactRule(artifactId, RuleType.VALIDITY, configDto);

        artifactRules = storage.getArtifactRules(artifactId);
        Assertions.assertNotNull(artifactRules);
        Assertions.assertFalse(artifactRules.isEmpty());
        Assertions.assertEquals(1, artifactRules.size());
        Assertions.assertEquals(RuleType.VALIDITY, artifactRules.get(0));
    }

    @Test
    void testUpdateArtifactRule() throws Exception {
        String artifactId = "testUpdateArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage.createArtifactRule(artifactId, RuleType.VALIDITY, configDto);

        RuleConfigurationDto rule = storage.getArtifactRule(artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("FULL", rule.getConfiguration());
        
        RuleConfigurationDto updatedConfig = new RuleConfigurationDto("NONE");
        storage.updateArtifactRule(artifactId, RuleType.VALIDITY, updatedConfig);

        rule = storage.getArtifactRule(artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("NONE", rule.getConfiguration());
    }

    @Test
    void testDeleteArtifactRule() throws Exception {
        String artifactId = "testDeleteArtifactRule-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage.createArtifactRule(artifactId, RuleType.VALIDITY, configDto);

        RuleConfigurationDto rule = storage.getArtifactRule(artifactId, RuleType.VALIDITY);
        Assertions.assertNotNull(rule);
        Assertions.assertEquals("FULL", rule.getConfiguration());
        
        storage.deleteArtifactRule(artifactId, RuleType.VALIDITY);

        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage.getArtifactRule(artifactId, RuleType.VALIDITY);
        });
    }

    @Test
    void testDeleteAllArtifactRulse() throws Exception {
        String artifactId = "testDeleteAllArtifactRulse-1";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ArtifactMetaDataDto dto = this.storage.createArtifact(artifactId, ArtifactType.OPENAPI, content).toCompletableFuture().get();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(artifactId, dto.getId());

        RuleConfigurationDto configDto = new RuleConfigurationDto("FULL");
        storage.createArtifactRule(artifactId, RuleType.VALIDITY, configDto);
        storage.createArtifactRule(artifactId, RuleType.COMPATIBILITY, configDto);

        List<RuleType> rules = storage.getArtifactRules(artifactId);
        Assertions.assertEquals(2, rules.size());
        
        storage.deleteArtifactRules(artifactId);

        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage.getArtifactRule(artifactId, RuleType.VALIDITY);
        });
        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            storage.getArtifactRule(artifactId, RuleType.COMPATIBILITY);
        });
    }
    
    @Test
    void testGlobalRules() {
        List<RuleType> globalRules = this.storage.getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());
        
        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration("FULL");
        this.storage.createGlobalRule(RuleType.COMPATIBILITY, config);
        
        RuleConfigurationDto rule = this.storage.getGlobalRule(RuleType.COMPATIBILITY);
        Assertions.assertEquals(rule.getConfiguration(), config.getConfiguration());
        
        globalRules = this.storage.getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertFalse(globalRules.isEmpty());
        Assertions.assertEquals(globalRules.size(), 1);
        Assertions.assertEquals(globalRules.get(0), RuleType.COMPATIBILITY);
        
        Assertions.assertThrows(RuleAlreadyExistsException.class, () -> {
            this.storage.createGlobalRule(RuleType.COMPATIBILITY, config);
        });
        
        RuleConfigurationDto updatedConfig = new RuleConfigurationDto("FORWARD");
        this.storage.updateGlobalRule(RuleType.COMPATIBILITY, updatedConfig);
        
        rule = this.storage.getGlobalRule(RuleType.COMPATIBILITY);
        Assertions.assertEquals(rule.getConfiguration(), updatedConfig.getConfiguration());
        
        Assertions.assertThrows(RuleNotFoundException.class, () -> {
            this.storage.updateGlobalRule(RuleType.VALIDITY, config);
        });
        
        storage.deleteGlobalRules();
        globalRules = this.storage.getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());

        this.storage.createGlobalRule(RuleType.COMPATIBILITY, config);
        this.storage.deleteGlobalRule(RuleType.COMPATIBILITY);
        globalRules = this.storage.getGlobalRules();
        Assertions.assertNotNull(globalRules);
        Assertions.assertTrue(globalRules.isEmpty());
    }

    @Test
    void testSearchArtifacts() throws Exception {
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
                    properties );
            this.storage.createArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, content, metaData ).toCompletableFuture().get();
        }
        
        long start = System.currentTimeMillis();
        
        ArtifactSearchResults results = storage.searchArtifacts("testSearchArtifacts", 0, 10, SearchOver.name, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(50, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(10, results.getArtifacts().size());
        
        results = storage.searchArtifacts("testSearchArtifacts-19-name", 0, 10, SearchOver.name, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-19-name", results.getArtifacts().get(0).getName());
        
        results = storage.searchArtifacts("testSearchArtifacts-33-description", 0, 10, SearchOver.description, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-33-name", results.getArtifacts().get(0).getName());
        
        results = storage.searchArtifacts(null, 0, 11, SearchOver.everything, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(11, results.getArtifacts().size());
        
        results = storage.searchArtifacts("testSearchArtifacts", 0, 1000, SearchOver.everything, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(50, results.getCount());
        Assertions.assertNotNull(results.getArtifacts());
        Assertions.assertEquals(50, results.getArtifacts().size());
        Assertions.assertEquals("testSearchArtifacts-1-name", results.getArtifacts().get(0).getName());
        Assertions.assertEquals("testSearchArtifacts-10-name", results.getArtifacts().get(1).getName());
        
        long end = System.currentTimeMillis();
        System.out.println("Search time: " + (end - start) + "ms");
    }

}
