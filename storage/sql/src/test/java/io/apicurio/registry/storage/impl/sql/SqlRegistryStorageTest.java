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
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
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
class SqlRegistryStorageTest {
    
    private static final String OPENAPI_CONTENT = "{" + 
            "    \"openapi\": \"3.0.2\"," + 
            "    \"info\": {" + 
            "        \"title\": \"Empty API\"," + 
            "        \"version\": \"1.0.0\"," + 
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
    }

}
