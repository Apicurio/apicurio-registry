package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.GitOps;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@Tag(FEATURE)
public class GitOpsITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(GitOpsITTest.class);

    private static final String EXAMPLE_REPO = "github.com/Apicurio/apicurio-registry-gitops-example";
    private static final String EXTERNAL_REPO_HINT =
            "This test depends on the external repository " + EXAMPLE_REPO + ". "
                    + "If it fails unexpectedly, verify the repository has not been modified.";

    @Test
    @DisplayName("GitOps local volume (self-contained, no internet)")
    void testGitOpsLocalVolume() throws Exception {
        applyTestDataConfigMap();

        var registry = deployGitOpsCR("example-local-volume.yaml");
        int port = portForwardToRegistry(registry);

        given()
                .get(new URI("http://localhost:" + port + "/apis/registry/v3/groups"))
                .then()
                .statusCode(200)
                .body("count", equalTo(1));
    }

    @Test
    @DisplayName("GitOps pull HTTPS [depends on " + EXAMPLE_REPO + "]")
    void testGitOpsPullHttps() throws Exception {
        var registry = deployGitOpsCR("example-pull-https.yaml");
        int port = portForwardToRegistry(registry);

        try {
            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> given()
                    .get(new URI("http://localhost:" + port + "/apis/registry/v3/groups"))
                    .then()
                    .statusCode(200)
                    .body("count", greaterThanOrEqualTo(2)));
        } catch (AssertionError e) {
            throw new AssertionError(EXTERNAL_REPO_HINT, e);
        }
    }

    @Test
    @DisplayName("GitOps dry-run validation [depends on " + EXAMPLE_REPO + "]")
    void testGitOpsValidation() throws Exception {
        var registry = deployGitOpsCR("example-pull-https.yaml");
        int port = portForwardToRegistry(registry);

        try {
            // Wait for initial data load
            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> given()
                    .get(new URI("http://localhost:" + port + "/apis/registry/v3/admin/gitops/status"))
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE")));

            // Successful validation: validate the test/valid-pr branch
            // (adds an optional field — backward compatible)
            var taskId = given()
                    .contentType("application/json")
                    .body("{\"type\": \"pull\", \"repoId\": \"default\", \"ref\": \"test/valid-pr\"}")
                    .post(new URI("http://localhost:" + port + "/apis/registry/v3/admin/gitops/validate"))
                    .then()
                    .statusCode(200)
                    .body("taskId", notNullValue())
                    .extract().path("taskId").toString();

            log.info("Validation task created: {}", taskId);

            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> given()
                    .get(new URI("http://localhost:" + port
                            + "/apis/registry/v3/admin/gitops/validate/" + taskId))
                    .then()
                    .statusCode(200)
                    .body("state", equalTo("completed"))
                    .body("result", equalTo("success"))
                    .body("groupCount", greaterThanOrEqualTo(1))
                    .body("artifactCount", greaterThanOrEqualTo(1)));

            log.info("Validation succeeded for 'test/valid-pr' branch");

            // Failed validation: validate the test/invalid-pr branch
            // (adds a required field without default — breaks BACKWARD compatibility)
            var failTaskId = given()
                    .contentType("application/json")
                    .body("{\"type\": \"pull\", \"repoId\": \"default\", \"ref\": \"test/invalid-pr\"}")
                    .post(new URI("http://localhost:" + port + "/apis/registry/v3/admin/gitops/validate"))
                    .then()
                    .statusCode(200)
                    .extract().path("taskId").toString();

            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> given()
                    .get(new URI("http://localhost:" + port
                            + "/apis/registry/v3/admin/gitops/validate/" + failTaskId))
                    .then()
                    .statusCode(200)
                    .body("state", equalTo("completed"))
                    .body("result", equalTo("failure")));

            log.info("Validation correctly failed for 'test/invalid-pr' branch (breaking compatibility)");

        } catch (AssertionError e) {
            throw new AssertionError(EXTERNAL_REPO_HINT, e);
        }
    }

    @Test
    @DisplayName("GitOps multi-repo HTTPS [depends on " + EXAMPLE_REPO + "]")
    void testGitOpsMultiRepo() throws Exception {
        var registry = deployGitOpsCR("example-multi-repo.yaml");
        int port = portForwardToRegistry(registry);

        try {
            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> given()
                    .get(new URI("http://localhost:" + port + "/apis/registry/v3/groups"))
                    .then()
                    .statusCode(200)
                    .body("count", greaterThanOrEqualTo(3)));

            await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
                var response = given()
                        .get(new URI(
                                "http://localhost:" + port + "/apis/registry/v3/admin/gitops/status"))
                        .then()
                        .statusCode(200)
                        .extract().body().jsonPath();
                assertThat(response.getString("syncState")).isEqualTo("IDLE");
                assertThat(response.getMap("sources")).containsKeys("platform", "fulfillment");
            });
        } catch (AssertionError e) {
            throw new AssertionError(EXTERNAL_REPO_HINT, e);
        }
    }

    @Test
    @DisplayName("GitOps push mode (self-contained, generates SSH keys)")
    void testGitOpsPush() throws Exception {
        // Generate SSH keys in a job pod (keepAlive so we can read the files)
        String publicKey;
        String privateKey;
        try (var keyGenJob = jobManager.runJob("""
                ssh-keygen -t ed25519 -f /tmp/gitops-key -N "" -q
                """, "alpine/git:latest", List.of(), List.of(), true)) {
            keyGenJob.waitForCompletion();
            publicKey = keyGenJob.readFile("/tmp/gitops-key.pub");
            privateKey = keyGenJob.readFile("/tmp/gitops-key");
        }

        log.info("Generated SSH key pair for push test");

        // Create the authorized_keys secret (public key — for the sidecar)
        client.resource(new SecretBuilder()
                .withNewMetadata()
                .withName("gitops-push-authorized-keys")
                .withNamespace(namespace)
                .endMetadata()
                .addToStringData("authorized_keys", publicKey)
                .build()).create();

        // Create the private key secret (for the push job to authenticate)
        client.resource(new SecretBuilder()
                .withNewMetadata()
                .withName("gitops-push-private-key")
                .withNamespace(namespace)
                .endMetadata()
                .addToStringData("id_ed25519", privateKey)
                .build()).create();

        applyTestDataConfigMap();

        // Deploy the push CR (don't wait for full readiness — registry won't be ready until data is pushed)
        var registry = deployGitOpsCRWithoutReadinessWait("example-push.yaml");

        // Wait for the SSH service and sidecar to be ready
        var sshServiceName = GitOps.getSshServiceName(registry);
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() ->
                assertThat(client.services().inNamespace(namespace)
                        .withName(sshServiceName).get()).isNotNull());

        log.info("SSH service {} created, pushing test data", sshServiceName);

        // Push test data to the sidecar via SSH
        var extraVolumes = List.of(
                new VolumeBuilder().withName("ssh-key")
                        .withSecret(new SecretVolumeSourceBuilder()
                                .withSecretName("gitops-push-private-key")
                                .withDefaultMode(0400).build())
                        .build(),
                new VolumeBuilder().withName("test-data")
                        .withNewConfigMap().withName("gitops-test-data").endConfigMap()
                        .build());
        var extraMounts = List.of(
                new VolumeMountBuilder().withName("ssh-key")
                        .withMountPath("/secrets").withReadOnly(true).build(),
                new VolumeMountBuilder().withName("test-data")
                        .withMountPath("/test-data").withReadOnly(true).build());

        try (var pushJob = jobManager.runJob(String.format("""
                set -ex
                export HOME=/tmp

                # Wait for sshd to be reachable
                for attempt in $(seq 1 30); do
                  if ssh-keyscan -p 2222 %s 2>/dev/null | grep -q ssh; then
                    echo "SSH server reachable (attempt $attempt)"
                    break
                  fi
                  echo "Waiting for SSH server... (attempt $attempt)"
                  sleep 2
                done

                # Configure SSH
                mkdir -p ~/.ssh
                cp /secrets/id_ed25519 ~/.ssh/id_ed25519
                chmod 600 ~/.ssh/id_ed25519
                ssh-keyscan -p 2222 %s >> ~/.ssh/known_hosts 2>/dev/null

                # Create a git repo from test data
                mkdir -p /tmp/repo/content
                for f in /test-data/*; do
                  name=$(basename "$f")
                  target=$(echo "$name" | sed 's/---/\\//g')
                  mkdir -p "/tmp/repo/$(dirname "$target")"
                  cp "$f" "/tmp/repo/$target"
                done
                cd /tmp/repo
                git init -b main
                git add -A
                git -c user.name=test -c user.email=test@test commit -m "init"

                # Push to the sidecar
                git remote add registry ssh://git@%s:2222/repos/default
                GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /tmp/.ssh/id_ed25519" \\
                  git push registry main
                """, sshServiceName, sshServiceName, sshServiceName),
                "alpine/git:latest", extraVolumes, extraMounts)) {
            boolean pushSucceeded = pushJob.waitAndIsSuccessful();
            if (!pushSucceeded) {
                log.error("Push job failed. Logs:\\n{}", pushJob.getLogs());
            }
            assertThat(pushSucceeded)
                    .as("Push job should succeed")
                    .isTrue();
        }

        log.info("Test data pushed, waiting for registry to load");

        // Wait for the registry to detect, debounce, load, and become ready
        await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() ->
                assertThat(client.apps().deployments()
                        .inNamespace(namespace)
                        .withName(registry.getMetadata().getName() + "-app-deployment").get()
                        .getStatus().getReadyReplicas()).isEqualTo(1));
        int port = portForwardToRegistry(registry);

        await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> given()
                .get(new URI("http://localhost:" + port + "/apis/registry/v3/groups"))
                .then()
                .statusCode(200)
                .body("count", equalTo(1)));
    }

    private void applyTestDataConfigMap() {
        var configMap = client.load(
                GitOpsITTest.class.getResourceAsStream(
                        "/k8s/examples/gitops/gitops-test-data.configmap.yaml"))
                .items();
        configMap.forEach(r -> {
            r.getMetadata().setNamespace(namespace);
            client.resource(r).createOrReplace();
        });
    }

    private ApicurioRegistry3 deployGitOpsCRWithoutReadinessWait(String exampleFile) {
        var registry = ResourceFactory.deserialize(
                "/k8s/examples/gitops/" + exampleFile,
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Wait for the pod to exist (not necessarily ready)
        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() ->
                assertThat(client.pods().inNamespace(namespace)
                        .withLabel("app", registry.getMetadata().getName())
                        .list().getItems()).isNotEmpty());

        return registry;
    }

    private ApicurioRegistry3 deployGitOpsCR(String exampleFile) {
        var registry = ResourceFactory.deserialize(
                "/k8s/examples/gitops/" + exampleFile,
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Use LONG_DURATION because GitOps needs the sidecar to clone first
        await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() ->
                assertThat(client.apps().deployments()
                        .inNamespace(namespace)
                        .withName(registry.getMetadata().getName() + "-app-deployment").get()
                        .getStatus().getReadyReplicas()).isEqualTo(1));

        return registry;
    }

    private int portForwardToRegistry(ApicurioRegistry3 registry) {
        var serviceName = registry.getMetadata().getName() + "-app-service";
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() ->
                assertThat(client.services().inNamespace(namespace)
                        .withName(serviceName).get()).isNotNull());
        int port = portForwardManager.startServicePortForward(serviceName, 8080);
        log.info("Registry available at http://localhost:{}", port);
        return port;
    }
}
