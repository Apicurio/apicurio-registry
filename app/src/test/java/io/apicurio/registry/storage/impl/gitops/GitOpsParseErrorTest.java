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
 * A metadata file that fails to parse (#11111) must fail the load, naming the file and the parse
 * error, instead of being silently skipped: before this fix, the file's artifact (and all its
 * versions) would simply disappear from a running registry, with no error anywhere.
 */
@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsParseErrorTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void brokenArtifactFileFailsTheLoadInsteadOfDisappearingSilently() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        // --- Load valid data first ---
        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("petstore"), withContext(() -> storage.getArtifactIds(10)));
        });

        // --- Now load a repository where the artifact file has a YAML syntax error ---
        testRepository.load("git/parse-error-broken-artifact");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("ERROR"))
                    .body("errors", hasSize(1))
                    .body("errors[0].detail", containsString("Could not parse file"))
                    .body("errors[0].detail", containsString("artifact-petstore.registry.yaml"));
        });
        // The registry keeps its last good data; the artifact doesn't silently disappear.
        assertEquals(Set.of("petstore"), withContext(() -> storage.getArtifactIds(10)));
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
