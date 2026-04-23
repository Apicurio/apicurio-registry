package io.apicurio.registry.auth.opawasm;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.styra.opa.wasm.OpaPolicy;
import com.styra.opa.wasm.OpaPolicyPool;

import io.apicurio.registry.storage.dto.SearchedArtifactDto;
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
    void searchResultFilteringWorks() {
        List<SearchedArtifactDto> artifacts = List.of(
                artifact("team-a", "schema-1"),
                artifact("team-a", "schema-2"),
                artifact("team-b", "schema-3"),
                artifact("shared", "schema-4"),
                artifact("team-b", "schema-5"),
                artifact("shared", "schema-6")
        );

        List<SearchedArtifactDto> allowed = controller.filterSearchResults("alice", Set.of(), artifacts);

        List<String> allowedNames = allowed.stream()
                .map(a -> a.getGroupId() + "/" + a.getArtifactId())
                .toList();

        Assertions.assertEquals(4, allowed.size());
        Assertions.assertTrue(allowedNames.contains("team-a/schema-1"));
        Assertions.assertTrue(allowedNames.contains("team-a/schema-2"));
        Assertions.assertTrue(allowedNames.contains("shared/schema-4"));
        Assertions.assertTrue(allowedNames.contains("shared/schema-6"));
        Assertions.assertFalse(allowedNames.contains("team-b/schema-3"));
        Assertions.assertFalse(allowedNames.contains("team-b/schema-5"));
    }

    @Test
    void searchResultFilteringAdminSeesAll() {
        List<SearchedArtifactDto> artifacts = List.of(
                artifact("team-a", "schema-1"),
                artifact("team-b", "schema-2"),
                artifact("shared", "schema-3")
        );

        List<SearchedArtifactDto> allowed = controller.filterSearchResults("admin", Set.of("sr-admin"), artifacts);
        Assertions.assertEquals(3, allowed.size());
    }

    private static SearchedArtifactDto artifact(String groupId, String artifactId) {
        SearchedArtifactDto dto = new SearchedArtifactDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        return dto;
    }
}
