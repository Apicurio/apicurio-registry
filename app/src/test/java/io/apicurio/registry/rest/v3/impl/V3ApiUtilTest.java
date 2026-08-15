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

package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ContractMetadata;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for V3ApiUtil, focused on the contract metadata projected from artifact labels.
 */
class V3ApiUtilTest {

    private static ArtifactMetaData convert(Map<String, String> labels) {
        return V3ApiUtil.dtoToArtifactMetaData(ArtifactMetaDataDto.builder()
                .groupId("g").artifactId("a").labels(labels).build());
    }

    @Test
    void testNoLabelsMeansNoContractMetadata() {
        assertNull(convert(null).getContractMetadata());
        assertNull(convert(Map.of()).getContractMetadata());
        assertNull(convert(Map.of("team", "payments")).getContractMetadata());
    }

    @Test
    void testNamespacedLabelsAreProjected() {
        ContractMetadata cm = convert(Map.of(
                "contract.orders.id", "orders",
                "contract.orders.status", "STABLE",
                "contract.orders.owner.team", "payments")).getContractMetadata();

        assertEquals(ContractMetadata.Status.STABLE, cm.getStatus());
        assertEquals("payments", cm.getOwnerTeam());
    }

    @Test
    void testLabelsWithoutContractIdFallBackToBarePrefix() {
        ContractMetadata cm = convert(Map.of(
                "contract.status", "DRAFT",
                "contract.owner.team", "payments")).getContractMetadata();

        assertEquals(ContractMetadata.Status.DRAFT, cm.getStatus());
        assertEquals("payments", cm.getOwnerTeam());
    }

    @Test
    void testInvalidEnumValueIsSkippedWithoutLosingOtherFields() {
        ContractMetadata cm = convert(Map.of(
                "contract.status", "NOT_A_REAL_STATUS",
                "contract.owner.team", "payments")).getContractMetadata();

        assertNull(cm.getStatus());
        assertEquals("payments", cm.getOwnerTeam());
    }

    @Test
    void testBareContractIdLabelDoesNotBlowUp() {
        assertDoesNotThrow(() -> convert(Map.of("contract.id", "orders")));
    }

    @Test
    void testDottedContractIdIsNotDetected() {
        ContractMetadata cm = convert(Map.of(
                "contract.a.b.id", "ignored",
                "contract.status", "DRAFT")).getContractMetadata();

        assertEquals(ContractMetadata.Status.DRAFT, cm.getStatus());
    }

    @Test
    void testAllContractFieldsAreProjected() {
        ContractMetadata cm = convert(Map.of(
                "contract.orders.id", "orders",
                "contract.orders.status", "DEPRECATED",
                "contract.orders.owner.team", "payments",
                "contract.orders.owner.domain", "commerce",
                "contract.orders.support.contact", "team@example.com",
                "contract.orders.classification", "INTERNAL",
                "contract.orders.stage", "PROD",
                "contract.orders.lifecycle.deprecated-date", "2026-01-01",
                "contract.orders.lifecycle.deprecation-reason", "replaced by v2",
                "contract.orders.compatibility.group", "orders-v1")).getContractMetadata();

        assertEquals(ContractMetadata.Status.DEPRECATED, cm.getStatus());
        assertEquals("commerce", cm.getOwnerDomain());
        assertEquals("team@example.com", cm.getSupportContact());
        assertEquals(ContractMetadata.Classification.INTERNAL, cm.getClassification());
        assertEquals(ContractMetadata.Stage.PROD, cm.getStage());
        assertEquals("2026-01-01", cm.getDeprecatedDate());
        assertEquals("replaced by v2", cm.getDeprecationReason());
        assertEquals("orders-v1", cm.getCompatibilityGroup());
    }
}