package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GitOpsReadOnlyApiTest {

    private static final String V3_BASE = "/apis/registry/v3";
    private static final String GROUP_ID = "foo";
    private static final String ARTIFACT_ID = "petstore";

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeAll
    void loadTestData() {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            org.junit.jupiter.api.Assertions.assertEquals(
                    Set.of(ARTIFACT_ID), storage.getArtifactIds(10));
        });
    }

    @Test
    void createGroupIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("groupId", "rejected-group"))
                .post(V3_BASE + "/groups")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void createGlobalRuleIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD"))
                .post(V3_BASE + "/admin/rules")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void updateGlobalRuleIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("ruleType", "VALIDITY", "config", "SYNTAX_ONLY"))
                .put(V3_BASE + "/admin/rules/{ruleType}", "VALIDITY")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void deleteGlobalRuleIsRejected() {
        given().delete(V3_BASE + "/admin/rules/{ruleType}", "VALIDITY")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void deleteAllGlobalRulesIsRejected() {
        given().delete(V3_BASE + "/admin/rules")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void createArtifactRuleIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("ruleType", "VALIDITY", "config", "FULL"))
                .post(V3_BASE + "/groups/{groupId}/artifacts/{artifactId}/rules", GROUP_ID, ARTIFACT_ID)
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void updateArtifactRuleIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("ruleType", "COMPATIBILITY", "config", "FULL"))
                .put(V3_BASE + "/groups/{groupId}/artifacts/{artifactId}/rules/{ruleType}",
                        GROUP_ID, ARTIFACT_ID, "COMPATIBILITY")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void deleteArtifactRuleIsRejected() {
        given().delete(V3_BASE + "/groups/{groupId}/artifacts/{artifactId}/rules/{ruleType}",
                        GROUP_ID, ARTIFACT_ID, "COMPATIBILITY")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void updateArtifactMetadataIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("name", "modified"))
                .put(V3_BASE + "/groups/{groupId}/artifacts/{artifactId}", GROUP_ID, ARTIFACT_ID)
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }

    @Test
    void deleteArtifactIsRejectedAsNotAllowed() {
        given().delete(V3_BASE + "/groups/{groupId}/artifacts/{artifactId}", GROUP_ID, ARTIFACT_ID)
                .then()
                .statusCode(405);
    }

    @Test
    void deleteGroupIsRejectedAsNotAllowed() {
        given().delete(V3_BASE + "/groups/{groupId}", GROUP_ID)
                .then()
                .statusCode(405);
    }

    @Test
    void importDataIsRejected() {
        given().contentType("application/zip")
                .body(new byte[]{0x50, 0x4b, 0x03, 0x04})
                .post(V3_BASE + "/admin/import")
                .then()
                .statusCode(409);
    }

    @Test
    void createPeerIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("peerId", "rest-peer", "url", "https://rest.example.com"))
                .post(V3_BASE + "/admin/peers")
                .then()
                .statusCode(409)
                .body("name", equalTo("ReadOnlyStorageException"));
    }
}
