package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.util.JsonObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsValidateTest {

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
                        {"type": "pull", "repoId": "default", "ref": "main"}
                        """)
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .body("taskId", notNullValue())
                .body("state", notNullValue())
                .body("repoId", equalTo("default"))
                .body("ref", equalTo("main"));
    }

    @Test
    void listTasksReturnsCreatedTask() {
        var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "default", "ref": "main"}
                        """)
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
                        {"type": "pull", "repoId": "default", "ref": "main"}
                        """)
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
                        {"type": "pull", "repoId": "default", "ref": "main"}
                        """)
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
                        {"type": "pull", "repoId": "default", "ref": "main"}
                        """)
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
            org.junit.jupiter.api.Assertions.assertNotEquals("pending", state);
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
                        {"type": "pull", "repoId": "default", "ref": "main"}
                        """)
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
            org.junit.jupiter.api.Assertions.assertNotEquals("pending", state);
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
