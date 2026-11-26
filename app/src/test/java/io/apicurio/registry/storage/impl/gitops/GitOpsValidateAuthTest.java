package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.util.GitopsAuthTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(GitopsAuthTestProfile.class)
public class GitOpsValidateAuthTest {

    private static final String ADMIN_USER = "alice";
    private static final String ADMIN_PASS = "alice";
    private static final String DEVELOPER_USER = "bob";
    private static final String DEVELOPER_PASS = "bob";
    private static final String READONLY_USER = "duncan";
    private static final String READONLY_PASS = "duncan";

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
    }

    @Test
    void adminCanCreateTask() {
        given()
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .body("taskId", notNullValue());
    }

    @Test
    void adminCanListTasks() {
        given()
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200);

        given()
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
                .when()
                .get("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void adminCanGetTask() {
        final var taskId = given()
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
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
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(200)
                .body("taskId", equalTo(taskId));
    }

    @Test
    void readOnlyUserCannotCreateTask() {
        given()
                .auth().preemptive().basic(READONLY_USER, READONLY_PASS)
                .contentType("application/json")
                .body("""
                        {"type": "pull", "repoId": "%s", "ref": "main"}
                        """.formatted(repoId()))
                .when()
                .post("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(403);
    }

    @Test
    void readOnlyUserCannotListTasks() {
        given()
                .auth().preemptive().basic(READONLY_USER, READONLY_PASS)
                .when()
                .get("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(403);
    }

    @Test
    void readOnlyUserCannotGetTask() {
        final var taskId = given()
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
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
                .auth().preemptive().basic(READONLY_USER, READONLY_PASS)
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(403);
    }

    @Test
    void developerCannotListTasks() {
        given()
                .auth().preemptive().basic(DEVELOPER_USER, DEVELOPER_PASS)
                .when()
                .get("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(403);
    }

    @Test
    void developerCannotGetTask() {
        final var taskId = given()
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
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
                .auth().preemptive().basic(DEVELOPER_USER, DEVELOPER_PASS)
                .when()
                .get("/apis/registry/v3/admin/gitops/validate/" + taskId)
                .then()
                .statusCode(403);
    }

    @Test
    void anonymousCannotListTasks() {
        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/validate")
                .then()
                .statusCode(401);
    }

    @Test
    void anonymousCannotGetTask() {
        final var taskId = given()
                .auth().preemptive().basic(ADMIN_USER, ADMIN_PASS)
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
                .statusCode(401);
    }

    @Test
    void anonymousCanReadGitOpsStatus() {
        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/status")
                .then()
                .statusCode(200);
    }
}
