package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsMultiRepoTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsMultiRepoTestProfile.class)
public class GitOpsMultiRepoTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void multiRepoAggregation() {
        var repoA = GitMultiRepoTestManager.getRepoA();
        var repoB = GitMultiRepoTestManager.getRepoB();

        // Load different data into each repo
        repoA.load("git/multi-repo-a");
        repoB.load("git/multi-repo-b");

        // Wait for both repos to be loaded — should see artifacts from both
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            Set<String> artifacts = withContext(() -> storage.getArtifactIds(100));
            assertEquals(Set.of("widget", "gadget"), artifacts);
        });

        // Verify groups from both repos
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var groups = withContext(() -> Set.copyOf(storage.getGroupIds(100)));
            assertEquals(Set.of("alpha", "beta"), groups);
        });

        // Verify artifact from repo A
        var widgetMeta = storage.getArtifactMetaData("alpha", "widget");
        assertEquals("JSON", widgetMeta.getArtifactType());

        // Verify artifact from repo B
        var gadgetMeta = storage.getArtifactMetaData("beta", "gadget");
        assertEquals("JSON", gadgetMeta.getArtifactType());

        // Verify status API shows composite marker and per-repo details
        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/status")
                .then()
                .statusCode(200)
                .body("syncState", equalTo("IDLE"))
                .body("sources.repo-a", notNullValue())
                .body("sources.repo-b", notNullValue());
    }

    @Test
    void conflictDetection() {
        var repoA = GitMultiRepoTestManager.getRepoA();
        var repoB = GitMultiRepoTestManager.getRepoB();

        // Ensure repo A has data with artifact alpha:widget
        repoA.load("git/multi-repo-a");

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            Set<String> artifacts = withContext(() -> storage.getArtifactIds(100));
            assertTrue(artifacts.contains("widget"),
                    "Expected 'widget' in artifacts: " + artifacts);
        });

        // Load conflicting data into repo B (same alpha:widget as repo A)
        repoB.load("git/conflict-b");

        // Wait for the conflict to be detected — status should show ERROR with structured errors
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("ERROR"))
                    .body("errors.detail", hasItem(
                            containsString("defined in multiple sources")))
                    .body("errors.source", hasItem(notNullValue()));
        });

        // Previous data should still be served (blue-green swap did not happen)
        var artifacts = withContext(() -> storage.getArtifactIds(100));
        assertTrue(artifacts.contains("widget"),
                "Previous data should still be served after conflict");
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
