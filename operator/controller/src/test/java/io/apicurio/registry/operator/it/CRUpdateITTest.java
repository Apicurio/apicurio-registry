package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
public class CRUpdateITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(CRUpdateITTest.class);

    @Test
    void testCRUpdate() {

        var testCases = List.of(
                List.of(
                        "/k8s/examples/simple-deprecated.apicurioregistry3.yaml",
                        "/k8s/examples/simple.apicurioregistry3.yaml"
                ),
                List.of(
                        "/k8s/examples/postgresql/example-postgresql-deprecated.apicurioregistry3.yaml",
                        "/k8s/examples/postgresql/example-postgresql.apicurioregistry3.yaml"
                ),
                List.of(
                        "/k8s/examples/kafkasql/plain/example-kafkasql-plain-deprecated.apicurioregistry3.yaml",
                        "/k8s/examples/kafkasql/plain/example-kafkasql-plain.apicurioregistry3.yaml"
                )
        );

        testCases.forEach(testCase -> {

            var deprecated = ResourceFactory.deserialize(testCase.get(0), ApicurioRegistry3.class);
            var updatedExpected = ResourceFactory.deserialize(testCase.get(1), ApicurioRegistry3.class);

            client.resource(deprecated).create();
            await().ignoreExceptionsInstanceOf(KubernetesClientException.class)
                    .timeout(Duration.ofSeconds(60)).untilAsserted(() -> {
                        var updated = client
                                .resources(ApicurioRegistry3.class).list().getItems().stream().filter(r -> r
                                        .getMetadata().getName().equals(deprecated.getMetadata().getName()))
                                .toList();
                        assertThat(updated).hasSize(1);
                        // We do not care about the operand here, just about the CR structure
                        assertThat(updated.get(0).getSpec()).usingRecursiveComparison()
                                // We have to specially handle generated Secret name, since we do not know it in advance.
                                // It should be enough to just make sure it's not blank.
                                .withEqualsForFields((l, r) -> !isBlank((String) l) && !isBlank((String) r),
                                        "app.storage.sql.dataSource.password.name")
                                .isEqualTo(updatedExpected.getSpec());
                    });
        });
    }

    /**
     * Covers the password migration path specifically: the plaintext value from the deprecated
     * `app.sql.dataSource.password` field must survive the move into a Secret, the Secret must be owned by
     * the CR, and repeated reconciles must not create additional Secrets.
     */
    @Test
    void testCRUpdatePasswordSecret() {
        var deprecated = ResourceFactory.deserialize(
                "/k8s/examples/postgresql/example-postgresql-deprecated.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        var plaintextPassword = deprecated.getSpec().getApp().getSql().getDataSource().getPassword();

        client.resource(deprecated).create();

        var secretName = new String[1];
        await().ignoreExceptionsInstanceOf(KubernetesClientException.class).timeout(Duration.ofSeconds(60))
                .untilAsserted(() -> {
                    var updated = client.resources(ApicurioRegistry3.class)
                            .withName(deprecated.getMetadata().getName()).get();
                    assertThat(updated).isNotNull();
                    var passwordRef = ofNullable(updated.getSpec())
                            .map(s -> s.getApp())
                            .map(a -> a.getStorage())
                            .map(st -> st.getSql())
                            .map(sql -> sql.getDataSource())
                            .map(ds -> ds.getPassword())
                            .orElse(null);
                    assertThat(passwordRef).isNotNull();
                    assertThat(passwordRef.getName()).isNotBlank();
                    secretName[0] = passwordRef.getName();
                });

        var secret = client.secrets().inNamespace(namespace).withName(secretName[0]).get();
        assertThat(secret).isNotNull();
        var decodedPassword = new String(Base64.getDecoder().decode(secret.getData().get("password")));
        assertThat(decodedPassword).isEqualTo(plaintextPassword);
        assertThat(secret.getMetadata().getOwnerReferences()).hasSize(1);
        assertThat(secret.getMetadata().getOwnerReferences().get(0).getName())
                .isEqualTo(deprecated.getMetadata().getName());
        var secretUid = secret.getMetadata().getUid();

        // Force another reconcile of an already-migrated CR and make sure the Secret is not recreated.
        var current = client.resources(ApicurioRegistry3.class).withName(deprecated.getMetadata().getName())
                .get();
        current.getMetadata().setLabels(Map.of("test-touch", "1"));
        client.resource(current).patch();

        await().pollDelay(Duration.ofSeconds(10)).timeout(Duration.ofSeconds(60)).untilAsserted(() -> {
            var secretsWithPrefix = client.secrets().inNamespace(namespace).list().getItems().stream()
                    .filter(s -> s.getMetadata().getName().startsWith(secretName[0])).toList();
            assertThat(secretsWithPrefix).hasSize(1);
            assertThat(secretsWithPrefix.get(0).getMetadata().getUid()).isEqualTo(secretUid);
        });
    }
}
