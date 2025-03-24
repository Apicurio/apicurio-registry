package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.UUID;

import static org.awaitility.Awaitility.await;

public class JobManager {

    private final KubernetesClient client;
    private final HostAliasManager hostAliasManager;

    public JobManager(KubernetesClient client, HostAliasManager hostAliasManager) {
        this.client = client;
        this.hostAliasManager = hostAliasManager;
    }

    /**
     * Run a job in the cluster, with the given bash script as an input.
     */
    public JobHandle runJob(String bashScript) {
        var suffix = UUID.randomUUID().toString().substring(0, 7);

        // @formatter:off
        var data = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName("data-" + suffix)
                .endMetadata()
                .addToData("script.sh", bashScript)
                .build();
        // @formatter:on

        // @formatter:off
        var job = new JobBuilder()
                .withNewMetadata()
                    .withName("job-" + suffix)
                .endMetadata()
                .withNewSpec()
                    .withNewTemplate()
                        .withNewMetadata()
                            .withName("worker-" + suffix)
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("worker")
                                .withImage("registry.access.redhat.com/ubi9/ubi:latest") // Fedora?
                                .withCommand("/bin/bash", "/mnt/data/script.sh")
                                .addNewVolumeMount()
                                    .withName("data")
                                    .withMountPath("/mnt/data")
                                .endVolumeMount()
                            .endContainer()
                            .addNewVolume()
                                .withName("data")
                                .withNewConfigMap()
                                    .withName("data-" + suffix)
                                .endConfigMap()
                            .endVolume()
                            .withRestartPolicy("Never")
                        .endSpec()
                    .endTemplate()
                    .withBackoffLimit(0)
                .endSpec()
                .build();
        // @formatter:on

        hostAliasManager.inject(job.getSpec().getTemplate());

        client.resource(data).create();
        client.resource(job).create();

        return new JobHandle(client, suffix);
    }

    public static class JobHandle implements AutoCloseable {

        private final KubernetesClient client;

        private final String suffix;

        JobHandle(KubernetesClient client, String suffix) {
            this.client = client;
            this.suffix = suffix;
        }

        /**
         * Wait until the job finishes.
         *
         * @return true if the job has been successful (script returned code 0).
         */
        public boolean waitAndIsSuccessful() {
            await().ignoreExceptions().until(() -> {
                var status = client.batch().v1().jobs().withName("job-" + suffix).get().getStatus();
                return status.getSucceeded() == 1 || status.getFailed() == 1;
            });
            return isSuccessful();
        }

        public boolean isSuccessful() {
            var status = client.batch().v1().jobs().withName("job-" + suffix).get().getStatus();
            return status.getSucceeded() == 1;
        }

        @Override
        public void close() {
            client.configMaps().withName("data-" + suffix).delete();
            client.batch().v1().jobs().withName("job-" + suffix).delete();
        }
    }
}
