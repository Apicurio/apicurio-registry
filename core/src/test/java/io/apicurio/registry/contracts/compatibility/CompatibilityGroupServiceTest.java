package io.apicurio.registry.contracts.compatibility;

import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompatibilityGroupServiceTest {

    private CompatibilityGroupService service;
    private RegistryStorage storage;

    @BeforeEach
    void setUp() {
        storage = mock(RegistryStorage.class);
        service = new CompatibilityGroupService();
        service.storage = storage;
    }

    @Test
    void testGetCompatibilityGroup() {
        Map<String, String> labels = new HashMap<>();
        labels.put("contract.mycontract.compatibility.group", "group-v1");
        labels.put("contract.mycontract.status", "STABLE");

        ArtifactMetaDataDto meta = ArtifactMetaDataDto.builder().labels(labels).build();
        when(storage.getArtifactMetaData("g", "a")).thenReturn(meta);

        String result = service.getCompatibilityGroup("g", "a", "mycontract");
        assertEquals("group-v1", result);
    }

    @Test
    void testGetCompatibilityGroupMissing() {
        ArtifactMetaDataDto meta = ArtifactMetaDataDto.builder()
                .labels(new HashMap<>()).build();
        when(storage.getArtifactMetaData("g", "a")).thenReturn(meta);

        assertNull(service.getCompatibilityGroup("g", "a", "mycontract"));
    }

    @Test
    void testSetCompatibilityGroupDoesNotWipeOtherLabels() {
        String contractId = "mycontract";
        String expectedKey = ContractLabels.key(contractId,
                ContractLabels.SUFFIX_COMPATIBILITY_GROUP);

        service.setCompatibilityGroup("g", "a", contractId, "group-v2");

        verify(storage).mergeArtifactLabels(
                eq("g"), eq("a"),
                eq(expectedKey),
                eq(Map.of(expectedKey, "group-v2")));
    }

    @Test
    void testSetCompatibilityGroupPrefixIsFullKey() {
        String contractId = "orders";

        service.setCompatibilityGroup("g", "a", contractId, "orders-v1");

        String expectedPrefix = "contract.orders.compatibility.group";
        verify(storage).mergeArtifactLabels(
                anyString(), anyString(),
                eq(expectedPrefix),
                anyMap());
    }
}
