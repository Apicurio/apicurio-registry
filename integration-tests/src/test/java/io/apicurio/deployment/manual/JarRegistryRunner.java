package io.apicurio.deployment.manual;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class JarRegistryRunner implements RegistryRunner {

    private static final Logger log = LoggerFactory.getLogger(JarRegistryRunner.class);

    private static final String KAFKASQL_REGISTRY_JAR_WORK_PATH = "../storage/kafkasql/target";
    private static final String KAFKASQL_REGISTRY_JAR_PATH = "apicurio-registry-storage-kafkasql-%s-runner.jar";
    private static final String PROJECT_VERSION = System.getProperty("project.version");

    private volatile Thread runner;

    private volatile boolean running;

    private volatile boolean stop;

    @Getter
    private volatile int nodeId;

    @Getter
    private Map<String, Object> report = new ConcurrentHashMap<>();


    public synchronized void start(int nodeId, Instant startingLine, String image, String bootstrapServers, List<String> args, BiConsumer<String, RegistryRunner> reporter) {
        if (!isStopped()) {
            throw new IllegalStateException("Already running.");
        }
        if (image != null) {
            throw new UnsupportedOperationException("image is not supported");
        }

        this.nodeId = nodeId;
        report.clear();

        runner = new Thread(() -> {
            Process process = null;
            try {
                var builder = new ProcessBuilder();
                builder.directory(new File(KAFKASQL_REGISTRY_JAR_WORK_PATH));
                builder.environment().put("REGISTRY_LOG_LEVEL", "DEBUG");

                var c = new ArrayList<String>();

                c.add("java");
                c.add(String.format("-Dquarkus.http.port=%s", 8780 + nodeId));
                c.add(String.format("-Dregistry.kafkasql.bootstrap.servers=%s", bootstrapServers));
                c.addAll(args);

                c.add("-jar");
                c.add(String.format(KAFKASQL_REGISTRY_JAR_PATH, PROJECT_VERSION));

                builder.command(c);
                builder.redirectErrorStream(true);

                while (!stop) {
                    try {
                        if (Instant.now().isAfter(startingLine)) {
                            process = builder.start();
                            break;
                        } else {
                            Thread.sleep(Duration.between(Instant.now(), startingLine).abs().toMillis());
                        }
                    } catch (IOException ex) {
                        log.error("Could not start Registry instance.", ex);
                        stop = true;
                    } catch (InterruptedException e) {
                        // stop?
                    }
                }
                if (!stop && process != null) {
                    log.info("Started process: '{}'. PID = {}, nodeId = '{}'.", String.join(" ", c), process.pid(), nodeId);
                    running = true;
                    try (Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
                        while (!stop) {
                            if (scanner.hasNextLine()) {
                                var line = scanner.nextLine();
                                LoggerFactory.getLogger("node " + nodeId).info(line);
                                reporter.accept(line, this);
                            }
                        }
                    }
                }
            } finally {
                if (process != null) {
                    process.destroy();
                }
                stop = false;
                running = false;
            }
        });
        runner.start();
    }


    @Override
    public String getClientURL() {
        if(!isStarted()) {
            throw new IllegalStateException("Not started.");
        }
        return String.format("http://localhost:" + (8780 + nodeId));
    }


    @Override
    public boolean isStarted() {
        return running;
    }


    @Override
    public boolean isStopped() {
        return !isStarted();
    }


    @Override
    public void stop() {
        if (isStarted()) {
            stop = true;
            runner.interrupt();
        }
    }


    @Override
    public void stopAndWait() {
        if (isStarted()) {
            stop();
            Awaitility.await().atMost(60, TimeUnit.SECONDS).until(this::isStopped);
        }
    }


    public static boolean isSupported() {
        return Files.exists(Path.of(KAFKASQL_REGISTRY_JAR_WORK_PATH, String.format(KAFKASQL_REGISTRY_JAR_PATH, PROJECT_VERSION)));
    }
}

