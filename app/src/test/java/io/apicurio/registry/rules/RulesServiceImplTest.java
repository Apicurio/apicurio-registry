/*
 * Copyright 2026 Red Hat
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

package io.apicurio.registry.rules;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RulesServiceImplTest {

    private RulesServiceImpl rulesService;
    private RegistryStorage storage;

    @BeforeEach
    public void setup() {
        rulesService = new RulesServiceImpl();
        storage = mock(RegistryStorage.class);
        rulesService.storage = storage;
        rulesService.factory = mock(RuleExecutorFactory.class);
        rulesService.otelMetrics = mock(OTelMetricsProvider.class);
        rulesService.rulesProperties = mock(RulesProperties.class);

        when(storage.isArtifactExists(any(), any())).thenReturn(true);
        when(storage.getArtifactRules(any(), any())).thenReturn(Collections.singletonList(RuleType.VALIDITY));
        when(storage.getArtifactRule(any(), any(), any())).thenReturn(new RuleConfigurationDto("FULL"));
        when(rulesService.rulesProperties.getDefaultGlobalRules()).thenReturn(Collections.emptySet());
        when(rulesService.factory.createExecutor(any())).thenReturn(context -> {});
    }

    @Test
    public void testApplyRulesWithDefaults() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        rulesService.applyRules(context);

        verify(storage).getArtifactRules("testGroup", "testArtifact");
        verify(rulesService.factory).createExecutor(RuleType.VALIDITY);
    }

    @Test
    public void testCreateModeDoesNotFetchArtifactRules() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);

        // Even though artifact exists, CREATE-typed requests must NOT fetch artifact rules
        when(storage.isArtifactExists("testGroup", "testArtifact")).thenReturn(true);

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.CREATE)
                .build();

        rulesService.applyRules(context);

        verify(storage, never()).getArtifactRules("testGroup", "testArtifact");
    }

    @Test
    public void testNullRuleConfigurationIsNotSkipped() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);

        when(storage.getArtifactRule("testGroup", "testArtifact", RuleType.VALIDITY))
                .thenReturn(new RuleConfigurationDto(null));

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        RuleExecutor executorMock = mock(RuleExecutor.class);
        when(rulesService.factory.createExecutor(RuleType.VALIDITY)).thenReturn(executorMock);

        rulesService.applyRules(context);

        org.mockito.ArgumentCaptor<RuleContext> captor = org.mockito.ArgumentCaptor.forClass(RuleContext.class);
        verify(executorMock).execute(captor.capture());
        Assertions.assertNull(captor.getValue().getConfiguration());
    }

    @Test
    public void testCustomStorageOverride() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);
        RegistryStorage customStorage = mock(RegistryStorage.class);
        when(customStorage.getArtifactRules("testGroup", "testArtifact")).thenReturn(Collections.singletonList(RuleType.VALIDITY));
        when(customStorage.getArtifactRule("testGroup", "testArtifact", RuleType.VALIDITY)).thenReturn(new RuleConfigurationDto("FULL"));

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .storage(customStorage)
                .build();

        RuleExecutor executorMock = mock(RuleExecutor.class);
        when(rulesService.factory.createExecutor(RuleType.VALIDITY)).thenReturn(executorMock);

        rulesService.applyRules(context);

        verify(customStorage).getArtifactRules("testGroup", "testArtifact");
        verify(storage, never()).getArtifactRules(any(), any());

        org.mockito.ArgumentCaptor<RuleContext> captor = org.mockito.ArgumentCaptor.forClass(RuleContext.class);
        verify(executorMock).execute(captor.capture());
        Assertions.assertEquals(customStorage, captor.getValue().getStorage());
    }

    @Test
    public void testApplyRulesWithVersion() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);
        StoredArtifactVersionDto versionDto = StoredArtifactVersionDto.builder()
                .content(ContentHandle.create("{\"type\":\"string\"}"))
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();
        when(storage.getArtifactVersionContent("testGroup", "testArtifact", "1.0.0")).thenReturn(versionDto);

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactVersion("1.0.0")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        rulesService.applyRules(context);

        verify(storage).getArtifactVersionContent("testGroup", "testArtifact", "1.0.0");
        verify(rulesService.factory).createExecutor(RuleType.VALIDITY);
    }

    @Test
    public void testApplyRulesWithContentIds() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);

        when(storage.getEnabledArtifactContentIds("testGroup", "testArtifact")).thenReturn(Collections.emptyList());

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        rulesService.applyRules(context);

        verify(storage).getEnabledArtifactContentIds("testGroup", "testArtifact");
        verify(rulesService.factory).createExecutor(RuleType.VALIDITY);
    }

    @Test
    public void testApplyRulesCreateMode() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);

        // Global rules DO apply in CREATE mode
        when(storage.getGlobalRules()).thenReturn(Collections.singletonList(RuleType.VALIDITY));
        when(storage.getGlobalRule(RuleType.VALIDITY)).thenReturn(new RuleConfigurationDto("FULL"));

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.CREATE)
                .build();

        rulesService.applyRules(context);

        verify(storage, never()).getArtifactRules(any(), any());
        verify(storage, never()).getEnabledArtifactContentIds(any(), any());
        verify(rulesService.factory).createExecutor(RuleType.VALIDITY);
    }

    @Test
    public void testApplySingleRule() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);

        RuleApplicationContext context = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleType(RuleType.VALIDITY)
                .ruleConfiguration("FULL")
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        rulesService.applyRule(context);

        verify(rulesService.factory).createExecutor(RuleType.VALIDITY);
    }

    @Test
    public void testApplyRuleNullGuards() {
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRules((RuleApplicationContext) null));
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRule((RuleApplicationContext) null));

        TypedContent content = TypedContent.create(ContentHandle.create("{}"), ContentTypes.APPLICATION_JSON);

        // Missing artifactId
        RuleApplicationContext contextMissingArtifactId = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRules(contextMissingArtifactId));

        // Missing artifactType
        RuleApplicationContext contextMissingArtifactType = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRules(contextMissingArtifactType));

        // Missing content
        RuleApplicationContext contextMissingContent = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRules(contextMissingContent));

        // Missing ruleApplicationType
        RuleApplicationContext contextMissingType = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .build();
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRules(contextMissingType));

        // Missing ruleType for applyRule
        RuleApplicationContext contextWithoutRuleType = RuleApplicationContext.builder()
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRule(contextWithoutRuleType));
    }
}
