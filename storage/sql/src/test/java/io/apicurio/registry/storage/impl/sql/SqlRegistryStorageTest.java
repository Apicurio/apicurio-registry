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

import java.util.List;
import java.util.SortedSet;

import javax.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
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
    
    

}
