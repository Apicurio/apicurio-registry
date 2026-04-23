package io.apicurio.registry.auth.opawasm;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.styra.opa.wasm.OpaPolicy;
import com.styra.opa.wasm.OpaPolicyPool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OpaWasmAccessControllerTest {

    private OpaWasmAccessController controller;

    @BeforeEach
    void setUp() throws Exception {
        URL wasmUrl = getClass().getClassLoader().getResource("registry-authz.wasm");
        Assertions.assertNotNull(wasmUrl, "registry-authz.wasm not found on classpath");

        InputStream dataStream = getClass().getClassLoader().getResourceAsStream("test-opa-permissions.json");
        Assertions.assertNotNull(dataStream, "test-opa-permissions.json not found on classpath");
        String permissionsData = new String(dataStream.readAllBytes(), StandardCharsets.UTF_8);

        Path wasmPath = Path.of(wasmUrl.toURI());
        OpaPolicyPool pool = OpaPolicyPool.create(
                () -> OpaPolicy.builder().withPolicy(wasmPath).build(), 2);

        controller = new OpaWasmAccessController();
        controller.initialize(pool, permissionsData);
    }

    @Test
    void aliceCanReadOwnArtifact() {
        Assertions.assertTrue(controller.evaluate("alice", Set.of(), "read", "artifact", "team-a/my-schema"));
    }

    @Test
    void aliceCanWriteOwnArtifact() {
        Assertions.assertTrue(controller.evaluate("alice", Set.of(), "write", "artifact", "team-a/my-schema"));
    }

    @Test
    void aliceCannotWriteSharedArtifact() {
        Assertions.assertFalse(controller.evaluate("alice", Set.of(), "write", "artifact", "shared/common-schema"));
    }

    @Test
    void aliceCanReadSharedArtifact() {
        Assertions.assertTrue(controller.evaluate("alice", Set.of(), "read", "artifact", "shared/common-schema"));
    }

    @Test
    void aliceCannotReadTeamBArtifact() {
        Assertions.assertFalse(controller.evaluate("alice", Set.of(), "read", "artifact", "team-b/their-schema"));
    }

    @Test
    void bobCanReadAnyArtifact() {
        Assertions.assertTrue(controller.evaluate("bob", Set.of(), "read", "artifact", "team-a/my-schema"));
        Assertions.assertTrue(controller.evaluate("bob", Set.of(), "read", "artifact", "team-b/their-schema"));
        Assertions.assertTrue(controller.evaluate("bob", Set.of(), "read", "artifact", "shared/common-schema"));
    }

    @Test
    void bobCanWriteOnlyToTeamB() {
        Assertions.assertTrue(controller.evaluate("bob", Set.of(), "write", "artifact", "team-b/their-schema"));
        Assertions.assertFalse(controller.evaluate("bob", Set.of(), "write", "artifact", "team-a/my-schema"));
    }

    @Test
    void adminCanDoEverything() {
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "read", "artifact", "team-a/x"));
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "write", "artifact", "team-b/y"));
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "admin", "artifact", "shared/z"));
    }

    @Test
    void unknownUserDenied() {
        Assertions.assertFalse(controller.evaluate("unknown", Set.of(), "read", "artifact", "team-a/my-schema"));
    }

    @Test
    void anonymousUserDenied() {
        Assertions.assertFalse(controller.evaluate("anonymous", Set.of(), "read", "artifact", "team-a/x"));
    }

    @Test
    void groupLevelAuthorizationWorks() {
        Assertions.assertTrue(controller.evaluate("alice", Set.of(), "read", "group", "team-a"));
        Assertions.assertTrue(controller.evaluate("alice", Set.of(), "write", "group", "team-a"));
        Assertions.assertFalse(controller.evaluate("alice", Set.of(), "admin", "group", "team-a"));
        Assertions.assertFalse(controller.evaluate("alice", Set.of(), "read", "group", "team-b"));
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "admin", "group", "team-b"));
    }

    @Test
    void impliesWorks() {
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "admin", "artifact", "x"));
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "write", "artifact", "x"));
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "read", "artifact", "x"));
    }

    @Test
    void defaultGroupDeniedWhenNoGrant() {
        Assertions.assertFalse(controller.evaluate("alice", Set.of(), "read", "artifact", "default/my-schema"));
    }

    @Test
    void defaultGroupAllowedForAdmin() {
        Assertions.assertTrue(controller.evaluate("admin", Set.of("sr-admin"), "read", "artifact", "default/my-schema"));
    }

    @Test
    void buildResourceNameHandlesNull() {
        Assertions.assertEquals("default/art1", OpaWasmAccessController.buildResourceName(null, "art1"));
        Assertions.assertEquals("my-group/art1", OpaWasmAccessController.buildResourceName("my-group", "art1"));
    }

    @Test
    void grantsDataParsing() {
        GrantsData data = controller.getGrantsData();
        Assertions.assertNotNull(data);
        Assertions.assertTrue(data.isAdmin(Set.of("sr-admin")));
        Assertions.assertFalse(data.isAdmin(Set.of("sr-developer")));
    }

    @Test
    void grantsDataAllowedGroups() {
        GrantsData data = controller.getGrantsData();
        Set<String> aliceGroups = data.getAllowedGroups("alice", Set.of(), "artifact");
        Assertions.assertNotNull(aliceGroups);
        Assertions.assertTrue(aliceGroups.contains("team-a"));
        Assertions.assertTrue(aliceGroups.contains("shared"));
        Assertions.assertFalse(aliceGroups.contains("team-b"));
    }

    @Test
    void grantsDataWildcardReturnsNull() {
        GrantsData data = controller.getGrantsData();
        Set<String> bobGroups = data.getAllowedGroups("bob", Set.of(), "artifact");
        Assertions.assertNull(bobGroups);
    }

    @Test
    void grantsDataMalformedJsonFailsClosed() {
        GrantsData data = GrantsData.parse("not valid json{{{");
        Assertions.assertFalse(data.isAdmin(Set.of("sr-admin")));
        Set<String> groups = data.getAllowedGroups("alice", Set.of(), "artifact");
        Assertions.assertNotNull(groups);
        Assertions.assertTrue(groups.isEmpty());
    }

    @Test
    void grantsDataEmptyJsonFailsClosed() {
        GrantsData data = GrantsData.parse("");
        Assertions.assertFalse(data.isAdmin(Set.of("anything")));
        Set<String> groups = data.getAllowedGroups("alice", Set.of(), "artifact");
        Assertions.assertNotNull(groups);
        Assertions.assertTrue(groups.isEmpty());
    }

    @Test
    void uninitializedControllerDeniesAccess() {
        OpaWasmAccessController uninit = new OpaWasmAccessController();
        Assertions.assertFalse(uninit.evaluate("alice", Set.of(), "read", "artifact", "team-a/x"));
    }
}
