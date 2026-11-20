package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.rest.v3.beans.GitOpsValidateTask;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.util.JsonObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsValidateTest {

    @Inject
    GitOpsConfig gitOpsConfig;

    @Inject
    GitOpsValidationTaskManager taskManager;

    private String repoId() {
        return gitOpsConfig.getRepos().get(0).id();
    }

    @BeforeEach
    void cleanup() {
        taskManager.removeAllTasks();
        taskManager.setValidationStartBarrier(() -> { });
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

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd", "ABCDEF012345", "not-a-valid-id", "abcdef0123456"})
    void getTaskRejectsInvalidTaskId(String invalidId) {
        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/" + invalidId)
                .then()
                .statusCode(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd", "ABCDEF012345", "not-a-valid-id", "abcdef0123456"})
    void deleteTaskRejectsInvalidTaskId(String invalidId) {
        given()
                .when()
                .delete("/apis/registry/v3/admin/gitops/validate/" + invalidId)
                .then()
                .statusCode(404);
    }

    @Test
    void deleteTaskRemovesCompletedFromLocalMemory() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke01");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given().get("/apis/registry/v3/admin/gitops/status")
                    .then().statusCode(200)
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
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then().extract().path("state").toString();
            assertNotEquals("pending", state);
        });

        simulateSidecarFetch(taskId, testRepository);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given().get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then().statusCode(200)
                    .body("state", equalTo("completed"));
        });

        given()
                .when()
                .delete("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(204);

        given()
                .when()
                .delete("/apis/registry/v3/admin/gitops/validate/" + taskId)
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

    @Test
    void loadPendingTasksSkipsInvalidFileNames() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        final Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");
        Files.createDirectories(validateDir);

        final Instant now = Instant.now();
        final var invalidId1 = ValidationTaskId.encode("pull", repoId(), "invalid-name-ref",
                now, gitOpsConfig.getValidateHmacKey());
        final var invalidId2 = ValidationTaskId.encode("pull", repoId(), "traversal-ref",
                now, gitOpsConfig.getValidateHmacKey());
        final var invalidId3 = ValidationTaskId.encode("pull", repoId(), "mid-json-ref",
                now, gitOpsConfig.getValidateHmacKey());
        final var validId = ValidationTaskId.encode("pull", repoId(), "main",
                now, gitOpsConfig.getValidateHmacKey());
        final String validLocalName = validId.localFileName();

        final Path invalidFile = validateDir.resolve("not-a-valid-id.json");
        final Path traversalFile = validateDir.resolve("...json");
        final Path midJsonFile = validateDir.resolve("abc.json123456789.json");
        final Path validFile = validateDir.resolve(validLocalName + ".json");
        try {
            Files.writeString(invalidFile, """
                    {"apiVersion": "v1", "kind": "ValidateRequest",
                     "spec": {"taskId": "%s", "type": "pull", "repoId": "%s", "ref": "invalid-name-ref",
                              "requestedAt": "%s"}}
                    """.formatted(invalidId1.value(), repoId(), now.toString()));
            Files.writeString(traversalFile, """
                    {"apiVersion": "v1", "kind": "ValidateRequest",
                     "spec": {"taskId": "%s", "type": "pull", "repoId": "%s", "ref": "traversal-ref",
                              "requestedAt": "%s"}}
                    """.formatted(invalidId2.value(), repoId(), now.toString()));
            Files.writeString(midJsonFile, """
                    {"apiVersion": "v1", "kind": "ValidateRequest",
                     "spec": {"taskId": "%s", "type": "pull", "repoId": "%s", "ref": "mid-json-ref",
                              "requestedAt": "%s"}}
                    """.formatted(invalidId3.value(), repoId(), now.toString()));
            Files.writeString(validFile, """
                    {"apiVersion": "v1", "kind": "ValidateRequest",
                     "spec": {"taskId": "%s", "type": "pull", "repoId": "%s", "ref": "main",
                              "requestedAt": "%s"}}
                    """.formatted(validId.value(), repoId(), now.toString()));

            taskManager.loadPendingTasks();

            final var taskIds = given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getList("taskId", String.class);

            assertFalse(taskIds.contains(invalidId1.value()));
            assertFalse(taskIds.contains(invalidId2.value()));
            assertFalse(taskIds.contains(invalidId3.value()));
            assertTrue(taskIds.contains(validId.value()));
        } finally {
            Files.deleteIfExists(invalidFile);
            Files.deleteIfExists(traversalFile);
            Files.deleteIfExists(midJsonFile);
            Files.deleteIfExists(validFile);
        }
    }

    @Test
    void getTaskReturns404ForOldFormatId() {
        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/abcdef012345")
                .then()
                .statusCode(404);
    }

    @Test
    void getTaskDecodesUnknownSelfDescribingId() {
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

        taskManager.removeAllTasks();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .statusCode(200)
                    .body("taskId", equalTo(taskId))
                    .body("state", equalTo("submitted"));
        });
    }

    @Test
    void createTaskReturnsSelfDescribingId() {
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
        assertTrue(taskId.length() > 50);
        assertTrue(taskId.matches("^[A-Za-z0-9_-]+$"));

        final var id = new ValidationTaskId(taskId);
        final var payload = id.decode(gitOpsConfig.getValidateHmacKey(),
                gitOpsConfig.getValidateTaskTtlSeconds());
        assertEquals("pull", payload.type());
        assertEquals(repoId(), payload.repoId());
        assertEquals("main", payload.ref());
    }

    @Test
    void deleteTaskReturns409WhenSubmitted() {
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

        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .body("state", equalTo("submitted"));

        given()
                .when()
                .delete("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(409);
    }

    @Test
    void deleteAndSubmitDoNotLeaveOrphanState() throws Exception {
        final int iterations = 20;
        for (int i = 0; i < iterations; i++) {
            final var taskId = taskManager.addPendingTask("pull", repoId(), "race-ref-" + i);
            final var latch = new CountDownLatch(1);
            final var deleteResult = new AtomicBoolean(false);
            final var deleteThread = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    deleteResult.set(taskManager.deleteTask(taskId.value()));
                } catch (IllegalStateException e) {
                    // Expected when submit wins the race and moves the task to SUBMITTED
                }
            });
            final var submitThread = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                taskManager.submitPendingTasks();
            });

            deleteThread.start();
            submitThread.start();
            latch.countDown();
            deleteThread.join(5_000);
            submitThread.join(5_000);

            final var taskIds = taskManager.listTasks().stream()
                    .map(GitOpsValidateTask::getTaskId).toList();
            if (deleteResult.get()) {
                assertFalse(taskIds.contains(taskId.value()),
                        "Deleted task must not remain in memory");
                final Path requestFile = Path.of(gitOpsConfig.getWorkspace())
                        .resolve("validate").resolve(taskId.localFileName() + ".json");
                assertFalse(Files.exists(requestFile),
                        "Request file must be removed after successful DELETE");
            } else if (taskIds.contains(taskId.value())) {
                final var task = taskManager.listTasks().stream()
                        .filter(t -> t.getTaskId().equals(taskId.value()))
                        .findFirst().orElseThrow();
                assertNotEquals("pending", task.getState().value(),
                        "If not deleted, task must have advanced past PENDING");
            }

            taskManager.removeAllTasks();
        }
    }

    @Test
    void duplicatePostReturnsSameTaskWhilePending() {
        final var taskId1 = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "dup-test-ref"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        final var taskId2 = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "dup-test-ref"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        assertEquals(taskId1, taskId2);
    }

    @Test
    void concurrentPostsForSameTupleReturnSameTask() throws Exception {
        final int threadCount = 8;
        final var latch = new CountDownLatch(1);
        final var results = new CopyOnWriteArrayList<String>();
        final var threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                final var taskId = given()
                        .contentType("application/json")
                        .body("""
                                {"type": "pull", "repoId": "%s", "ref": "concurrent-ref"}
                                """.formatted(repoId()))
                        .when()
                        .post("/apis/registry/v3/admin/gitops/validate")
                        .then()
                        .statusCode(200)
                        .extract().path("taskId").toString();
                results.add(taskId);
            });
            threads[i].start();
        }

        latch.countDown();
        for (final var thread : threads) {
            thread.join(10_000);
        }

        assertEquals(threadCount, results.size());
        final var distinct = results.stream().distinct().count();
        assertEquals(1, distinct, "All concurrent POSTs must return the same task ID");
    }

    @Test
    void duplicatePostReturnsNewTaskAfterFetch() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke01");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given().get("/apis/registry/v3/admin/gitops/status")
                    .then().statusCode(200)
                    .body("syncState", equalTo("IDLE"));
        });

        final var taskId1 = given()
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
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId1)
                    .then().extract().path("state").toString();
            assertNotEquals("pending", state);
        });

        simulateSidecarFetch(taskId1, testRepository);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given().get("/apis/registry/v3/admin/gitops/validate/" + taskId1)
                    .then().statusCode(200)
                    .body("state", equalTo("completed"));
        });

        final var taskId2 = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        assertNotEquals(taskId1, taskId2);
    }

    @Test
    void loadPendingTasksRecoversSavedTaskId() throws Exception {
        final var taskId = given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "restart-test-ref"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .extract().path("taskId").toString();

        taskManager.removeAllTasks();

        final Path validateDir = Path.of(gitOpsConfig.getWorkspace()).resolve("validate");
        Files.createDirectories(validateDir);
        final String localName = new ValidationTaskId(taskId).localFileName();
        final Path requestFile = validateDir.resolve(localName + ".json");
        final var request = ValidationRequest.builder()
                .apiVersion(ValidationRequest.CURRENT_API_VERSION)
                .kind("ValidateRequest")
                .spec(ValidationRequest.Spec.builder()
                        .taskId(taskId)
                        .type("pull")
                        .repoId(repoId())
                        .ref("restart-test-ref")
                        .requestedAt(Instant.now().toString())
                        .build())
                .status(ValidationRequest.Status.builder()
                        .state(SidecarState.FETCHING)
                        .build())
                .build();
        JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(requestFile.toFile(), request);

        taskManager.loadPendingTasks();

        final var loadedIds = taskManager.listTasks().stream()
                .map(GitOpsValidateTask::getTaskId).toList();
        assertTrue(loadedIds.contains(taskId),
                "listTasks must contain the task loaded from the request file");

        final var loadedTask = taskManager.listTasks().stream()
                .filter(t -> t.getTaskId().equals(taskId))
                .findFirst().orElseThrow();
        assertEquals("fetching", loadedTask.getState().value());
        assertEquals(repoId(), loadedTask.getRepoId());
        assertEquals("restart-test-ref", loadedTask.getRef());
    }

    @Test
    void createTaskRejectsRefExceedingEncodedLength() {
        final String longRef = "€".repeat(120);
        given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "%s"}
                        """.formatted(repoId(), longRef))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(400);
    }

    @Test
    void deleteTaskReturns409WhenFetching() throws Exception {
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

        final String localName = localFileName(taskId);
        final Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");
        final Path requestFile = validateDir.resolve(localName + ".json");
        final var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);
        request.setStatus(ValidationRequest.Status.builder()
                .state(SidecarState.FETCHING)
                .build());
        JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(requestFile.toFile(), request);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then()
                    .body("state", equalTo("fetching"));
        });

        given()
                .when()
                .delete("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(409);
    }

    @Test
    void createTaskRejectsTooLongRef() {
        final String longRef = "refs/heads/" + "a".repeat(250);
        given()
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "%s"}
                        """.formatted(repoId(), longRef))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(400);
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

    @Test
    void getTaskReturnsValidatingWhileValidationRuns() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke01");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given().get("/apis/registry/v3/admin/gitops/status")
                    .then().statusCode(200)
                    .body("syncState", equalTo("IDLE"));
        });

        final var validationLatch = new CountDownLatch(1);
        final var enteredValidation = new CountDownLatch(1);
        taskManager.setValidationStartBarrier(() -> {
            enteredValidation.countDown();
            try {
                validationLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then().extract().path("state").toString();
            assertNotEquals("pending", state);
        });

        simulateSidecarFetch(taskId, testRepository);

        try {
            assertTrue(enteredValidation.await(15, TimeUnit.SECONDS),
                    "Validation must start within timeout");

            final var task = given()
                    .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then().statusCode(200)
                    .extract().as(GitOpsValidateTask.class);
            assertEquals("validating", task.getState().value(),
                    "GET must return 'validating' while validation is blocked");
        } finally {
            validationLatch.countDown();
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given().get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                    .then().statusCode(200)
                    .body("state", equalTo("completed"));
        });
    }

    /**
     * Simulates the sidecar by cloning the test repository into the validation checkout
     * directory and updating the request file with a completed status.
     */
    private String localFileName(String taskId) {
        return new ValidationTaskId(taskId).localFileName();
    }

    private void simulateSidecarFetch(String taskId, GitTestRepository testRepository) throws Exception {
        final String localName = localFileName(taskId);
        Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");
        Path checkoutDir = validateDir.resolve(localName).resolve("repo");
        Files.createDirectories(checkoutDir);

        Git.cloneRepository()
                .setURI(testRepository.getGitRepoUrl())
                .setDirectory(checkoutDir.toFile())
                .setBranch(testRepository.getGitRepoBranch())
                .call()
                .close();

        Path requestFile = validateDir.resolve(localName + ".json");
        var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);
        request.setStatus(ValidationRequest.Status.builder()
                .state(SidecarState.FETCHED)
                .checkoutPath("repo")
                .completedAt(Instant.now().toString())
                .build());
        JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(requestFile.toFile(), request);
    }

    private void simulateSidecarFetchWithTraversalPath(String taskId, String maliciousPath) throws Exception {
        final String localName = localFileName(taskId);
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        final Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");

        final Path requestFile = validateDir.resolve(localName + ".json");
        final var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);
        request.setStatus(ValidationRequest.Status.builder()
                .state(SidecarState.FETCHED)
                .checkoutPath(maliciousPath)
                .completedAt(Instant.now().toString())
                .build());
        JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(requestFile.toFile(), request);
    }

    private void simulateSidecarFetchWithData(String taskId, String dataSource) throws Exception {
        final String localName = localFileName(taskId);
        var testRepository = GitTestRepositoryManager.getTestRepository();
        Path validateDir = Path.of(testRepository.getGitRepoUrl()).getParent().resolve("validate");
        Path checkoutDir = validateDir.resolve(localName).resolve("repo");
        Files.createDirectories(checkoutDir);

        var sourcePath = Path.of(Thread.currentThread().getContextClassLoader()
                .getResource(dataSource).toURI());
        var git = Git.init().setDirectory(checkoutDir.toFile()).setInitialBranch("main").call();
        org.apache.commons.io.FileUtils.copyDirectory(sourcePath.toFile(), checkoutDir.toFile());
        git.add().addFilepattern(".").call();
        git.commit().setMessage("test data").call();
        git.close();

        Path requestFile = validateDir.resolve(localName + ".json");
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
