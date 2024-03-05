package io.apicurio.deployment.k8s;

import io.fabric8.kubernetes.api.model.HasMetadata;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.function.Consumer;

import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;

public class PodLogWatcher {

    private Thread thread;

    private final HasMetadata podRef;

    private final String tag;

    private final Consumer<String> logConsumer;

    private volatile boolean running;

    private volatile boolean stop;


    public PodLogWatcher(HasMetadata podRef, String tag, Consumer<String> logConsumer) {
        this.podRef = podRef;
        this.logConsumer = logConsumer;
        if (tag == null) {
            tag = "pod " + podRef.getMetadata().getName();
        }
        this.tag = tag;
    }


    public void start() {
        if (running) {
            throw new IllegalStateException("Already running.");
        }
        stop = false;
        thread = new Thread(() -> {
            try (var watch = kubernetesClient()
                    .pods()
                    .inNamespace(podRef.getMetadata().getNamespace())
                    .withName(podRef.getMetadata().getName())
                    .watchLog()
            ) {
                running = true;
                try (Scanner scanner = new Scanner(watch.getOutput(), StandardCharsets.UTF_8)) {
                    while (!stop) {
                        if (scanner.hasNextLine()) {
                            var line = scanner.nextLine();
                            LoggerFactory.getLogger(tag).info(line);
                            logConsumer.accept(line);
                        }
                    }
                }
            } catch (Exception ex) {
                LoggerFactory.getLogger(tag).error("Could not receive logs for '{}'.", tag, ex);
            } finally {
                running = false;
            }
        });
        thread.start();
    }


    public boolean isStarted() {
        return running && thread.isAlive();
    }


    public void stop() {
        if (thread != null) {
            stop = true;
        }
    }


    public void stopAndWait() {
        if (thread != null) {
            stop = true;
            try {
                thread.interrupt();
                thread.join(10 * 1000);
            } catch (InterruptedException ex) {
                LoggerFactory.getLogger(tag).error("Error:", ex);
            }
        }
    }
}
