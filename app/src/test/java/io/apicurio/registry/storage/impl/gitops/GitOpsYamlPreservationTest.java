package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
class GitOpsYamlPreservationTest {

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeEach
    void setup() {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/empty");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of()));
    }

    @Test
    void testYamlContentNotConvertedToJson() {
        assertEquals(Set.of(), storage.getArtifactIds(10));

        final var testRepository = GitTestRepositoryManager.getTestRepository();

        testRepository.load("git/smoke04");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("hello-api")));

        var version = storage.getArtifactVersionContent("com.example.yaml", "hello-api", "1");
        assertNotNull(version.getContent());

        String contentStr = new String(version.getContent().bytes());
        assertFalse(contentStr.trim().startsWith("{"),
                "YAML content should be preserved as YAML, not converted to JSON");
        assertTrue(contentStr.contains("openapi:"),
                "Content should contain YAML key format");
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
