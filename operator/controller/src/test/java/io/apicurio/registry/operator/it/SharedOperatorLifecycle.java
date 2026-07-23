package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.App;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.enterprise.inject.spi.CDI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

import static io.apicurio.registry.operator.it.ITBase.CRD_FILE;
import static io.apicurio.registry.operator.it.ITBase.SHORT_DURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

final class SharedOperatorLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SharedOperatorLifecycle.class);

    private static App localOperator;
    private static KubernetesClient localOperatorClient;

    private SharedOperatorLifecycle() {
    }

    static synchronized void ensureStarted(KubernetesClient client,
            ITBase.OperatorDeployment deploymentType, String deploymentTarget) throws IOException {
        if (deploymentType == ITBase.OperatorDeployment.remote) {
            OperatorClusterWideInstaller.ensureInstalled(client, deploymentTarget);
            return;
        }
        if (localOperator != null) {
            return;
        }
        localOperatorClient = new KubernetesClientBuilder().build();
        createCRDs(localOperatorClient);
        var app = CDI.current().select(App.class).get();
        app.start(configOverride -> {
            configOverride.withKubernetesClient(localOperatorClient);
            configOverride.withUseSSAToPatchPrimaryResource(false);
        });
        localOperator = app;
        log.info("Started the shared in-process operator for this JVM");
    }

    static synchronized void stopLocalOperator() {
        if (localOperator == null) {
            return;
        }
        log.info("Stopping the shared in-process operator");
        try {
            localOperator.stop();
        } finally {
            localOperator = null;
            localOperatorClient = null;
        }
    }

    private static void createCRDs(KubernetesClient client) {
        log.info("Creating CRDs");
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var crd = client.load(new FileInputStream(CRD_FILE));
                crd.createOrReplace();
                await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
                    crd.resources().forEach(r -> assertThat(r.get()).isNotNull());
                });
                return;
            } catch (Exception e) {
                last = e;
                log.warn("CRD creation attempt {}/3 failed", attempt, e);
            }
        }
        throw new IllegalStateException("Could not create the CRDs after 3 attempts", last);
    }
}
