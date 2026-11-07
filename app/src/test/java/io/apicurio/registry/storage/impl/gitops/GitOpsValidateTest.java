package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.util.JsonObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsValidateTest {

    @Inject
    GitOpsConfig gitOpsConfig;

    private String repoId() {
        return gitOpsConfig.getRepos().get(0).id();
    }

    @BeforeEach
    void cleanup() {
        // Delete all existing validation tasks to avoid interference between tests
        var tasks = given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("taskId", String.class);
        for (String taskId : tasks) {
            given().delete("/apis/registry/v3/admin/gitops/validate/" + taskId);
        }
    }

    @Test
    void createValidateTaskReturnsTaskId() {
        given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .body("taskId", notNullValue())
                .body("state", notNullValue())
                .body("repoId", equalTo(repoId()))
                .body("ref", equalTo("main"));
    }

    @Test
    void createValidateTaskRejectsUnknownRepoId() {
        given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "does-not-exist", "ref": "main"}
                        """)
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("name", equalTo("BadRequestException"))
                .body("detail", containsString("Repository 'does-not-exist' is not configured"))
                .body("detail", containsString(repoId()));
    }

    @Test
    void createValidateTaskRejectsMissingRepoId() {
        given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "ref": "main"}
                        """)
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("name", equalTo("BadRequestException"))
                .body("detail", containsString("Repository ID is required"))
                .body("detail", containsString(repoId()));
    }

    @Test
    void createValidateTaskRejectsEmptyRepoId() {
        given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "", "ref": "main"}
                        """)
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("name", equalTo("BadRequestException"))
                .body("detail", containsString("Repository ID is required"))
                .body("detail", containsString(repoId()));
    }

    @Test
    void listTasksReturnsCreatedTask() {
        var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void getTaskReturnsTask() {
        var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(200)
                .body("taskId", equalTo(taskId))
                .body("state", notNullValue());
    }

    @Test
    void deleteTaskRemovesIt() {
        var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        given()
                .when()
                .delete("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void validationSucceedsWithValidData() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke01");

        // Wait for the main repo to be loaded first
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE"));
        });

        // Create a validation task
        var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        // Wait for the task to be submitted (request file written)
        var testRepository2 = testRepository;
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var state = given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .extract().path("state").toString();
            assertNotEquals("pending", state);
        });

        // Simulate the sidecar: create a checkout from the test repo
        simulateSidecarFetch(taskId, testRepository2);

        // Wait for the validation to complete
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .statusCode(200)
                    .body("state", equalTo("completed"))
                    .body("result", equalTo("success"))
                    .body("groupCount", greaterThanOrEqualTo(1))
                    .body("artifactCount", greaterThanOrEqualTo(1))
                    .body("errors", empty());
        });
    }

    @Test
    void validationFailsWithInvalidData() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke01");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE"));
        });

        var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        // Wait for the task to be submitted
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var state = given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .extract().path("state").toString();
            assertNotEquals("pending", state);
        });

        // Simulate the sidecar with invalid data
        simulateSidecarFetchWithData(taskId, "git/rule-violation-compatibility");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .statusCode(200)
                    .body("state", equalTo("completed"))
                    .body("result", equalTo("failure"))
                    .body("errors", not(empty()));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../../../etc", "/etc", "repo/../../etc"})
    void validationRejectsMaliciousCheckoutPath(String maliciousPath) throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke01");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE"));
        });

        final var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            final var state = given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .extract().path("state").toString();
            assertNotEquals("pending", state);
        });

        simulateSidecarFetchWithTraversalPath(taskId, maliciousPath);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .statusCode(200)
                    .body("state", equalTo("failed"))
                    .body("errors.detail", hasItem("Invalid checkout path"));
        });
    }

    /**
     * Regression test for #9740: deleting a task must unlink a symlink found inside
     * the task directory, never delete through it. The sentinel lives outside the
     * workspace, so a cleanup that follows the link destroys it and the test fails.
     */
    @Test
    void deleteTaskRemovesSymlinkWithoutFollowingIt(@TempDir Path sentinelDir) throws Exception {
        final var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        // The sidecar creates this directory during a real fetch.
        final var validateDir = Path.of(gitOpsConfig.getWorkspace()).resolve("validate");
        final var taskDir = validateDir.resolve(taskId);
        final var requestFile = validateDir.resolve(taskId + ".json");
        Files.createDirectories(taskDir);
        final var sentinel = sentinelDir.resolve("sentinel.txt");
        Files.writeString(sentinel, "must survive the cleanup");
        try {
            Files.createSymbolicLink(taskDir.resolve("link"), sentinelDir);
        } catch (UnsupportedOperationException | FileSystemException e) {
            Assumptions.assumeTrue(false, "symbolic links are not available on this platform: " + e);
        }

        assertTrue(Files.exists(requestFile), "precondition: the request file must exist before the delete");

        given()
                .when()
                .delete("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(204);

        assertFalse(Files.exists(requestFile, LinkOption.NOFOLLOW_LINKS), "the request file should be deleted with the task");
        assertFalse(Files.exists(taskDir, LinkOption.NOFOLLOW_LINKS), "the task directory should be gone");
        assertTrue(Files.exists(sentinel), "the sentinel file was deleted through the link");
    }

    /**
     * Simulates the sidecar by cloning the test repository into the validation checkout
     * directory and updating the request file with a completed status.
     */
    private void simulateSidecarFetch(String taskId, GitTestRepository testRepository) throws Exception {
        Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");
        Path checkoutDir = validateDir.resolve(taskId).resolve("repo");
        Files.createDirectories(checkoutDir);

        // Clone the test repo into the checkout dir
        Git.cloneRepository()
                .setURI(testRepository.getGitRepoUrl())
                .setDirectory(checkoutDir.toFile())
                .setBranch(testRepository.getGitRepoBranch())
                .call()
                .close();

        // Update the request file with fetched status (simulating sidecar completion)
        Path requestFile = validateDir.resolve(taskId + ".json");
        var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);
        request.setStatus(ValidationRequest.Status.builder()
                .state(SidecarState.FETCHED)
                .checkoutPath("repo")
                .completedAt(Instant.now().toString())
                .build());
        JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(requestFile.toFile(), request);
    }

    /**
     * Simulates a malicious sidecar writing a path-traversal checkoutPath to the request file.
     */
    private void simulateSidecarFetchWithTraversalPath(String taskId, String maliciousPath) throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        final Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");

        final Path requestFile = validateDir.resolve(taskId + ".json");
        final var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);
        request.setStatus(ValidationRequest.Status.builder()
                .state(SidecarState.FETCHED)
                .checkoutPath(maliciousPath)
                .completedAt(Instant.now().toString())
                .build());
        JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(requestFile.toFile(), request);
    }

    /**
     * Simulates the sidecar by creating a fresh git repo with specific test data
     * and updating the request file.
     */
    private void simulateSidecarFetchWithData(String taskId, String dataSource) throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();
        Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");
        Path checkoutDir = validateDir.resolve(taskId).resolve("repo");
        Files.createDirectories(checkoutDir);

        // Create a fresh repo with the specified test data
        var sourcePath = Path.of(Thread.currentThread().getContextClassLoader()
                .getResource(dataSource).toURI());
        var git = Git.init().setDirectory(checkoutDir.toFile()).setInitialBranch("main").call();
        org.apache.commons.io.FileUtils.copyDirectory(sourcePath.toFile(), checkoutDir.toFile());
        git.add().addFilepattern(".").call();
        git.commit().setMessage("test data").call();
        git.close();

        // Update the request file with fetched status (simulating sidecar completion)
        Path requestFile = validateDir.resolve(taskId + ".json");
        var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);
        request.setStatus(ValidationRequest.Status.builder()
                .state(SidecarState.FETCHED)
                .checkoutPath("repo")
                .completedAt(Instant.now().toString())
                .build());
        JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(requestFile.toFile(), request);
    }
}
