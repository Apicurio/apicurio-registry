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
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RulesServiceImplTest {

    private RulesServiceImpl rulesService;
    private RegistryStorage storage;

    @BeforeEach
    public void setUp() {
        rulesService = new RulesServiceImpl();
        storage = mock(RegistryStorage.class);
        rulesService.storage = storage;
        rulesService.factory = mock(RuleExecutorFactory.class);
        rulesService.otelMetrics = mock(io.apicurio.registry.metrics.OTelMetricsProvider.class);
        rulesService.rulesProperties = mock(io.apicurio.registry.rules.config.RulesProperties.class);

        when(storage.getArtifactRules(any(), any())).thenReturn(Collections.singletonList(RuleType.VALIDITY));
        when(rulesService.factory.createExecutor(any())).thenReturn(context -> {});
    }

    @Test
    public void testApplyRulesWithExistingContent() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);
        List<TypedContent> existing = Collections.singletonList(content);

        RuleApplicationContext context = RuleApplicationContext.builder()
                .storage(storage)
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .existingContent(existing)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        rulesService.applyRules(context);

        verify(storage).getArtifactRules("testGroup", "testArtifact");
        verify(storage, never()).getArtifactVersionContent(any(), any(), any());
        verify(storage, never()).getEnabledArtifactContentIds(any(), any());
    }

    @Test
    public void testApplyRulesWithArtifactVersion() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);
        StoredArtifactVersionDto versionDto = StoredArtifactVersionDto.builder()
                .content(ContentHandle.create("{\"type\":\"string\"}"))
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        when(storage.getArtifactVersionContent("testGroup", "testArtifact", "1.0.0")).thenReturn(versionDto);

        RuleApplicationContext context = RuleApplicationContext.builder()
                .storage(storage)
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactVersion("1.0.0")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .build();

        rulesService.applyRules(context);

        verify(storage).getArtifactVersionContent("testGroup", "testArtifact", "1.0.0");
        verify(storage).getArtifactRules("testGroup", "testArtifact");
        verify(storage, never()).getEnabledArtifactContentIds(any(), any());
    }

    @Test
    public void testApplyRulesWithUpdateType() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);
        when(storage.getEnabledArtifactContentIds("testGroup", "testArtifact")).thenReturn(Collections.emptyList());

        RuleApplicationContext context = RuleApplicationContext.builder()
                .storage(storage)
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .build();

        rulesService.applyRules(context);

        verify(storage).getEnabledArtifactContentIds("testGroup", "testArtifact");
        verify(storage).getArtifactRules("testGroup", "testArtifact");
    }

    @Test
    public void testApplyRulesWithCreateType() {
        TypedContent content = TypedContent.create(ContentHandle.create("{\"type\":\"string\"}"), ContentTypes.APPLICATION_JSON);

        RuleApplicationContext context = RuleApplicationContext.builder()
                .storage(storage)
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(content)
                .ruleApplicationType(RuleApplicationType.CREATE)
                .build();

        rulesService.applyRules(context);

        verify(storage, never()).getArtifactRules(any(), any());
        verify(storage, never()).getEnabledArtifactContentIds(any(), any());
    }

    @Test
    public void testApplyRuleNullGuards() {
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRules((RuleApplicationContext) null));
        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRule((RuleApplicationContext) null));

        RuleApplicationContext contextWithoutRuleType = RuleApplicationContext.builder()
                .storage(storage)
                .groupId("testGroup")
                .artifactId("testArtifact")
                .artifactType(ArtifactType.JSON)
                .content(TypedContent.create(ContentHandle.create("{}"), ContentTypes.APPLICATION_JSON))
                .build();

        Assertions.assertThrows(NullPointerException.class, () -> rulesService.applyRule(contextWithoutRuleType));
    }
}
