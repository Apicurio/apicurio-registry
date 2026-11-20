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
import io.apicurio.registry.rest.v3.beans.BranchSearchResults;
import io.apicurio.registry.rest.v3.beans.ContractMetadata;
import io.apicurio.registry.rest.v3.beans.SearchedBranch;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.BranchSearchResultsDto;
import io.apicurio.registry.storage.dto.SearchedBranchDto;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
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

    private static SearchedBranchDto branch(String branchId, long createdOn, long modifiedOn) {
        return SearchedBranchDto.builder()
                .groupId("group-" + branchId)
                .artifactId("artifact-" + branchId)
                .branchId(branchId)
                .description("Description for " + branchId)
                .systemDefined(false)
                .owner("owner-" + branchId)
                .createdOn(createdOn)
                .modifiedBy("modifier-" + branchId)
                .modifiedOn(modifiedOn)
                .build();
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
    void testContractIdPrefixTakesPrecedenceOverBarePrefix() {
        ContractMetadata cm = convert(Map.of(
                "contract.orders.id", "orders",
                "contract.orders.status", "STABLE",
                "contract.status", "DRAFT")).getContractMetadata();

        assertEquals(ContractMetadata.Status.STABLE, cm.getStatus());
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

    @Test
    void testBranchSearchResultsNormalFlow() {
        SearchedBranchDto dtoBranch = branch("main", 1700000000000L, 1700000001000L);
        BranchSearchResultsDto dto = BranchSearchResultsDto.builder()
                .count(1)
                .branches(List.of(dtoBranch))
                .build();

        BranchSearchResults results = V3ApiUtil.dtoToSearchResults(dto);

        assertEquals(1, results.getCount());
        assertEquals(1, results.getBranches().size());

        SearchedBranch branch = results.getBranches().getFirst();
        assertEquals("group-main", branch.getGroupId());
        assertEquals("artifact-main", branch.getArtifactId());
        assertEquals("main", branch.getBranchId());
        assertEquals("Description for main", branch.getDescription());
        assertEquals(Boolean.FALSE, branch.getSystemDefined());
        assertEquals("owner-main", branch.getOwner());
        assertEquals(new Date(1700000000000L), branch.getCreatedOn());
        assertEquals("modifier-main", branch.getModifiedBy());
        assertEquals(new Date(1700000001000L), branch.getModifiedOn());
    }

    @Test
    void testBranchSearchResultsMultipleBranches() {
        BranchSearchResultsDto dto = BranchSearchResultsDto.builder()
                .count(2)
                .branches(List.of(
                        branch("main", 1700000000000L, 1700000001000L),
                        branch("release", 1700000002000L, 1700000003000L)))
                .build();

        BranchSearchResults results = V3ApiUtil.dtoToSearchResults(dto);

        assertEquals(2, results.getCount());
        assertEquals(2, results.getBranches().size());
        assertEquals("main", results.getBranches().get(0).getBranchId());
        assertEquals("release", results.getBranches().get(1).getBranchId());
        assertEquals("group-release", results.getBranches().get(1).getGroupId());
        assertEquals("artifact-release", results.getBranches().get(1).getArtifactId());
    }

    @Test
    void testBranchSearchResultsDateConversion() {
        long createdOn = 1712345678901L;
        long modifiedOn = 1712345689012L;
        BranchSearchResultsDto dto = BranchSearchResultsDto.builder()
                .count(1)
                .branches(List.of(branch("dates", createdOn, modifiedOn)))
                .build();

        SearchedBranch branch = V3ApiUtil.dtoToSearchResults(dto).getBranches().getFirst();

        assertEquals(new Date(createdOn), branch.getCreatedOn());
        assertEquals(createdOn, branch.getCreatedOn().getTime());
        assertEquals(new Date(modifiedOn), branch.getModifiedOn());
        assertEquals(modifiedOn, branch.getModifiedOn().getTime());
    }

    @Test
    void testBranchSearchResultsEmptyBranchList() {
        BranchSearchResultsDto dto = BranchSearchResultsDto.builder()
                .count(0)
                .branches(List.of())
                .build();

        BranchSearchResults results = V3ApiUtil.dtoToSearchResults(dto);

        assertEquals(0, results.getCount());
        assertEquals(List.of(), results.getBranches());
    }

    @Test
    void testBranchSearchResultsNullGroupAndArtifactIds() {
        SearchedBranchDto dtoBranch = SearchedBranchDto.builder()
                .groupId(null)
                .artifactId(null)
                .branchId("main")
                .description("Description for main")
                .systemDefined(false)
                .owner("owner-main")
                .createdOn(1700000000000L)
                .modifiedBy("modifier-main")
                .modifiedOn(1700000001000L)
                .build();

        BranchSearchResultsDto dto = BranchSearchResultsDto.builder()
                .count(1)
                .branches(List.of(dtoBranch))
                .build();

        BranchSearchResults results = V3ApiUtil.dtoToSearchResults(dto);

        SearchedBranch branch = results.getBranches().getFirst();

        assertNull(branch.getGroupId());
        assertNull(branch.getArtifactId());
    }
}
