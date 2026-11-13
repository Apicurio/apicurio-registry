package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static io.restassured.RestAssured.get;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Loads the same data in GitOps data format versions v0 and v1 (see ADR-0013): v0 is deprecated but
 * still read, a repository can mix both, and a file in a newer version fails the load instead of being
 * skipped.
 */
@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsFormatVersionsTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void readsV0V1AndMixedRepositoriesAndRejectsNewerVersions() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        // --- v0 only: deprecated, still loaded ---
        testRepository.load("git/format-v0");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("person", "user"), withContext(() -> storage.getArtifactIds(10)));
        });
        assertEquals("User Avro Schema", storage.getArtifactMetaData("bar", "user").getName());
        assertEquals("JSON", storage.getArtifactMetaData("bar", "person").getArtifactType());

        // --- v1, a different data set, to see that the next load is applied ---
        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("petstore"), withContext(() -> storage.getArtifactIds(10)));
        });

        // --- v0 and v1 files in one repository ---
        testRepository.load("git/format-mixed");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("person", "user"), withContext(() -> storage.getArtifactIds(10)));
        });
        assertEquals("User Avro Schema", storage.getArtifactMetaData("bar", "user").getName());
        assertEquals("JSON", storage.getArtifactMetaData("bar", "person").getArtifactType());

        // --- A newer version (artifact-v2) fails the load; the previous data stays ---
        testRepository.load("git/format-newer");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("ERROR"))
                    .body("errors", hasSize(1))
                    .body("errors[0].detail", containsString(
                            "uses $type artifact-v2, a newer GitOps data format version than this registry reads"));
        });
        assertEquals(Set.of("person", "user"), withContext(() -> storage.getArtifactIds(10)));
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
