package io.apicurio.registry.auth.grants;

import io.apicurio.authz.GrantsData;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GrantsAccessControllerTest {

    @Test
    void buildResourceNameWithGroup() {
        assertEquals("my-group/art1", GrantsAccessController.buildResourceName("my-group", "art1"));
    }

    @Test
    void buildResourceNameNullGroupDefaultsToDefault() {
        assertEquals("default/art1", GrantsAccessController.buildResourceName(null, "art1"));
    }

    @Test
    void uninitializedControllerDenies() {
        GrantsAccessController uninit = new GrantsAccessController();
        assertFalse(uninit.canReadArtifact("team-a", "x"));
    }

    @Test
    void uninitializedControllerGrantsDataNull() {
        GrantsAccessController uninit = new GrantsAccessController();
        assertNull(uninit.getGrantsData());
    }

    @Test
    void grantsDataParsingFromSharedModule() {
        String json = """
                {
                  "config": {"admin_roles": ["sr-admin"]},
                  "grants": [
                    {"principal": "alice", "operation": "write", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "team-a/"}
                  ]
                }""";
        GrantsData data = GrantsData.parse(json);
        assertNotNull(data);
        assertTrue(data.isAdmin(Set.of("sr-admin")));
        assertFalse(data.isAdmin(Set.of("sr-developer")));
        assertEquals(1, data.getGrantsForUser("alice", Set.of()).size());
        assertTrue(data.getGrantsForUser("bob", Set.of()).isEmpty());
    }

    @Test
    void grantsDataAllowedValuesFromSharedModule() {
        String json = """
                {
                  "grants": [
                    {"principal": "alice", "operation": "write", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
                    {"principal": "alice", "operation": "read", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "shared/"}
                  ]
                }""";
        GrantsData data = GrantsData.parse(json);
        Set<String> groups = data.getAllowedValues("alice", Set.of(), "artifact", "/");
        assertNotNull(groups);
        assertTrue(groups.contains("team-a"));
        assertTrue(groups.contains("shared"));
    }

    @Test
    void grantsDataMalformedJsonFailsClosed() {
        GrantsData data = GrantsData.parse("broken{{{");
        assertFalse(data.isAdmin(Set.of("sr-admin")));
        assertTrue(data.getGrantsForUser("anyone", Set.of()).isEmpty());
    }

    @Test
    void registryResourceTypeImplies() {
        assertTrue(RegistryResourceType.Artifact.Admin.implies().contains(RegistryResourceType.Artifact.Write));
        assertTrue(RegistryResourceType.Artifact.Admin.implies().contains(RegistryResourceType.Artifact.Read));
        assertTrue(RegistryResourceType.Artifact.Write.implies().contains(RegistryResourceType.Artifact.Read));
        assertTrue(RegistryResourceType.Artifact.Read.implies().isEmpty());
    }
}
