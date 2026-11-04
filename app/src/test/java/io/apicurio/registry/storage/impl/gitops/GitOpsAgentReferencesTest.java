package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
class GitOpsAgentReferencesTest {

    private static final String GROUP = "com.example.agents";

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void referenceGraphIsLoadedAndDisallowedTypesAreRejected() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        // The agent is declared before the tool and server it references, all in one commit.
        testRepository.load("git/agent-refs");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertEquals(
                Set.of("weather-agent", "weather-server", "weather-tool"),
                withContext(() -> storage.getArtifactIds(10))));

        assertEquals(Set.of("weather-server", "weather-agent"), withContext(() -> artifactIds(
                storage.getInboundArtifactReferences(GROUP, "weather-tool", "1"))));

        // A server referencing an agent violates the group's INTEGRITY rule, so the commit is not applied.
        testRepository.load("git/agent-refs-invalid");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> given()
                .get("/apis/registry/v3/admin/gitops/status")
                .then().statusCode(200).body("syncState", equalTo("ERROR"))
                .body("errors", hasSize(1))
                .body("errors[0].detail", containsString("Referenced artifact type is not allowed")));
        assertEquals(Set.of("weather-server", "weather-agent"), withContext(() -> artifactIds(
                storage.getInboundArtifactReferences(GROUP, "weather-tool", "1"))));
        assertEquals(Set.of("weather-tool"), withContext(() -> artifactIds(
                storage.getArtifactVersionContent(GROUP, "weather-server", "1").getReferences())));
    }

    private static Set<String> artifactIds(List<ArtifactReferenceDto> references) {
        return references.stream().map(ArtifactReferenceDto::getArtifactId).collect(Collectors.toSet());
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
