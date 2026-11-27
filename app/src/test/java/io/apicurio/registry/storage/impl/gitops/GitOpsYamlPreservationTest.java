package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GitOpsYamlPreservationTest {

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeAll
    void loadTestData() {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke04");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(Set.of("hello-api"), storage.getArtifactIds(10));
        });
    }

    @Test
    void testYamlContentNotConvertedToJson() {
        final var version = storage.getArtifactVersionContent("com.example.yaml", "hello-api", "1");
        assertNotNull(version.getContent());

        final String contentStr = new String(version.getContent().bytes());
        assertFalse(contentStr.trim().startsWith("{"),
                "YAML content should be preserved as YAML, not converted to JSON");
        assertTrue(contentStr.contains("openapi:"),
                "Content should contain YAML key format");
    }
}
