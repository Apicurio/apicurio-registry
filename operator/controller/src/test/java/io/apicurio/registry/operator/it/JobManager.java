package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

public class JobManager {

    private final KubernetesClient client;
    private final HostAliasManager hostAliasManager;

    private static final String DEFAULT_IMAGE = "registry.access.redhat.com/ubi9/ubi:latest";
    private static final String DONE_MARKER = "/tmp/.done";
    private static final String CLOSE_MARKER = "/tmp/.close";

    public JobManager(KubernetesClient client, HostAliasManager hostAliasManager) {
        this.client = client;
        this.hostAliasManager = hostAliasManager;
    }

    /**
     * Run a job in the cluster, with the given bash script as an input.
     */
    public JobHandle runJob(String bashScript) {
        return runJob(bashScript, DEFAULT_IMAGE, List.of(), List.of(), false);
    }

    /**
     * Run a job with a custom image and extra volumes.
     */
    public JobHandle runJob(String bashScript, String image,
            List<Volume> extraVolumes, List<VolumeMount> extraMounts) {
        return runJob(bashScript, image, extraVolumes, extraMounts, false);
    }

    /**
     * Run a job with a custom image and extra volumes.
     *
     * @param keepAlive if true, the pod stays running after the script completes,
     *                  allowing files to be read via {@link JobHandle#readFile(String)}.
     *                  Use {@link JobHandle#waitForCompletion()} to wait for the script to finish,
     *                  then read files, and finally call {@link JobHandle#close()} to clean up.
     */
    public JobHandle runJob(String bashScript, String image,
            List<Volume> extraVolumes, List<VolumeMount> extraMounts, boolean keepAlive) {
        var suffix = UUID.randomUUID().toString().substring(0, 7);

        var script = bashScript;
        if (keepAlive) {
            script += "\ntouch " + DONE_MARKER
                    + "\nwhile [ ! -f " + CLOSE_MARKER + " ]; do sleep 1; done\n";
        }

        // @formatter:off
        var data = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName("data-" + suffix)
                .endMetadata()
                .addToData("script.sh", script)
                .build();
        // @formatter:on

        // @formatter:off
        var jobBuilder = new JobBuilder()
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
                                .withImage(image)
                                .withCommand("/bin/sh", "-c", "sh /mnt/data/script.sh")
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
                .endSpec();
        // @formatter:on

        Job job = jobBuilder.build();

        var container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        extraMounts.forEach(m -> container.getVolumeMounts().add(m));
        extraVolumes.forEach(v -> job.getSpec().getTemplate().getSpec().getVolumes().add(v));

        hostAliasManager.inject(job.getSpec().getTemplate());

        client.resource(data).create();
        client.resource(job).create();

        return new JobHandle(client, suffix, keepAlive);
    }

    public static class JobHandle implements AutoCloseable {

        private final KubernetesClient client;
        private final String suffix;
        private final boolean keepAlive;

        JobHandle(KubernetesClient client, String suffix, boolean keepAlive) {
            this.client = client;
            this.suffix = suffix;
            this.keepAlive = keepAlive;
        }

        /**
         * Wait until the job finishes (pod terminates).
         *
         * @return true if the job has been successful (script returned code 0).
         */
        public boolean waitAndIsSuccessful() {
            await().ignoreExceptions().until(() -> {
                var status = client.batch().v1().jobs().withName("job-" + suffix).get().getStatus();
                return Integer.valueOf(1).equals(status.getSucceeded())
                        || Integer.valueOf(1).equals(status.getFailed());
            });
            return isSuccessful();
        }

        /**
         * Wait until the script finishes but the pod stays running (for keepAlive jobs).
         * After this returns, files can be read from the pod via {@link #readFile(String)}.
         *
         * @throws IllegalStateException if the job was not created with keepAlive=true
         */
        public void waitForCompletion() {
            if (!keepAlive) {
                throw new IllegalStateException("waitForCompletion() requires keepAlive=true");
            }
            await().ignoreExceptions().until(() -> {
                readFile(DONE_MARKER);
                return true;
            });
        }

        public boolean isSuccessful() {
            var status = client.batch().v1().jobs().withName("job-" + suffix).get().getStatus();
            return Integer.valueOf(1).equals(status.getSucceeded());
        }

        public String getLogs() {
            var pods = client.pods()
                    .withLabel("job-name", "job-" + suffix)
                    .list().getItems();
            if (pods.isEmpty()) {
                return "<no pod found>";
            }
            return client.pods().withName(pods.get(0).getMetadata().getName()).getLog();
        }

        /**
         * Read a file from the job pod. The pod must be running (use keepAlive=true).
         */
        public String readFile(String path) {
            var pod = client.pods()
                    .withLabel("job-name", "job-" + suffix)
                    .list().getItems().get(0);
            try (InputStream is = client.pods()
                    .withName(pod.getMetadata().getName())
                    .file(path)
                    .read()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file " + path + " from job pod", e);
            }
        }

        @Override
        public void close() {
            if (keepAlive) {
                try {
                    var pod = client.pods()
                            .withLabel("job-name", "job-" + suffix)
                            .list().getItems().get(0);
                    client.pods().withName(pod.getMetadata().getName())
                            .exec("touch", CLOSE_MARKER);
                } catch (Exception e) {
                    // Best-effort — job deletion will clean up anyway
                }
            }
            client.configMaps().withName("data-" + suffix).delete();
            client.batch().v1().jobs().withName("job-" + suffix).delete();
        }
    }
}
