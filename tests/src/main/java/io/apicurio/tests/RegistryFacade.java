/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.tests;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.executor.Exec;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.h2.tools.Server;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RegistryFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(RegistryFacade.class);

    private static final String REGISTRY_JAR_PATH_FORMAT = "../storage/%s/target/apicurio-registry-storage-%s-%s-runner.jar";
    private static final String REGISTRY_JAR_PATH = System.getenv().get("REGISTRY_JAR_PATH");
    private static final String PROJECT_VERSION = System.getProperty("project.version");
    private static final RegistryStorageType REGISTRY_STORAGE = RegistryStorageType.valueOf(System.getenv().getOrDefault("REGISTRY_STORAGE", "inmemory"));
    private static final RegistryQuarkusProfile QUARKUS_PROFILE = RegistryQuarkusProfile.valueOf(System.getProperty("quarkus.profile", "dev"));

    private LinkedList<RegistryTestProcess> processes = new LinkedList<>();

    /**
     * Method for start registries from jar file. New process is created.
     *
     * @throws Exception if registry depoyment fails
     */
    public void start() throws Exception {
        if (REGISTRY_STORAGE == null) {
            throw new IllegalStateException("REGISTRY_STORAGE env var is mandatory");
        }

        String path = getJarPath();
        if (path == null) {
            throw new IllegalStateException("Could not determine where to find the executable jar for the server. " +
                "This may happen if you are using an IDE to debug.");
        }
        LOGGER.info("Deploying registry with profile {}", QUARKUS_PROFILE.name());
        Map<String, String> appEnv = new HashMap<>();
        Map<String, String> appProperties = new HashMap<>();
        switch (REGISTRY_STORAGE) {
            case inmemory:
                path = String.format("../app/target/apicurio-registry-app-%s-runner.jar", PROJECT_VERSION);
                break;
            case infinispan:
                break;
            case kafka:
            case streams:
                setupKafkaStorage(appEnv, appProperties);
                break;
            case jpa:
                setupJPAStorage(appEnv);
                break;
        }

        runRegistry(path, appEnv, appProperties);

    }

    private void setupJPAStorage(Map<String, String> appEnv) throws Exception {
        if (QUARKUS_PROFILE == RegistryQuarkusProfile.dev) {
            Server server = Server.createTcpServer("-tcpPort", "9123", "-tcpAllowOthers", "-ifNotExists");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            server.setOut(new PrintStream(out));
            server.start();

            TestUtils.waitFor("Database is running",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, () -> server.isRunning(true));
            LOGGER.info("H2 database started, server status: {}", server.getStatus());

            processes.add(new RegistryTestProcess() {

                @Override
                public String getName() {
                    return "h2";
                }

                @Override
                public void close() throws Exception {
                    server.stop();
                }

                @Override
                public String getStdOut() {
                    return out.toString();
                }

                @Override
                public String getStdErr() {
                    return null;
                }
            });
        } else {
            PostgreSQLContainer database = new PostgreSQLContainer<>("postgres:10.12");
            database.start();
            TestUtils.waitFor("Database is running",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, database::isRunning);

            String datasourceUrl = "jdbc:postgresql://" + database.getContainerIpAddress() + ":" +
                    database.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database.getDatabaseName();
            appEnv.put("QUARKUS_DATASOURCE_URL", datasourceUrl);
            appEnv.put("QUARKUS_DATASOURCE_USERNAME", database.getUsername());
            appEnv.put("QUARKUS_DATASOURCE_PASSWORD", database.getPassword());
            processes.add(new RegistryTestProcess() {

                @Override
                public String getName() {
                    return "postgresql";
                }

                @Override
                public void close() throws Exception {
                    database.close();
                }

                @Override
                public String getStdOut() {
                    return database.getLogs(OutputType.STDOUT);
                }

                @Override
                public String getStdErr() {
                    return database.getLogs(OutputType.STDERR);
                }
            });
        }
    }

    private void setupKafkaStorage(Map<String, String> appEnv, Map<String, String> appProperties) throws TimeoutException, InterruptedException, ExecutionException {
        KafkaContainer kafka = new KafkaContainer();
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafka.start();

        String bootstrapServers = kafka.getBootstrapServers();
        LOGGER.info("Bootstrap servers {}", bootstrapServers);

        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("connections.max.idle.ms", 10000);
        properties.put("request.timeout.ms", 5000);
        try (AdminClient client = KafkaAdminClient.create(properties)) {
            CreateTopicsResult result = client.createTopics(Arrays.asList(
                    new NewTopic("storage-topic", 1, (short) 1),
                    new NewTopic("global-id-topic", 1, (short) 1)
                    ));
            result.all().get(15, TimeUnit.SECONDS);
            LOGGER.info("Topics created");
        }

        if (QUARKUS_PROFILE == RegistryQuarkusProfile.dev) {
            appProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        } else {
            appEnv.put("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers);
            if (REGISTRY_STORAGE == RegistryStorageType.streams) {
                appEnv.put("APPLICATION_ID", "test-application");
            }
        }

        processes.add(new RegistryTestProcess() {

            @Override
            public String getName() {
                return "kafka";
            }

            @Override
            public void close() throws Exception {
                kafka.close();
            }

            @Override
            public String getStdOut() {
                return kafka.getLogs(OutputType.STDOUT);
            }

            @Override
            public String getStdErr() {
                return kafka.getLogs(OutputType.STDERR);
            }
        });
    }

    private void runRegistry(String path, Map<String, String> appEnv, Map<String, String> extraProps) {
        Exec executor = new Exec();
        LOGGER.info("Starting Registry app from: {}", path);
        CompletableFuture.supplyAsync(() -> {
            try {

                List<String> cmd = new ArrayList<>();
                cmd.add("java");
                cmd.addAll(Arrays.asList(
                        // "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                        "-Dquarkus.http.port=8081",
                        "-Dquarkus.log.console.level=DEBUG",
                        "-Dquarkus.log.category.\"io\".level=DEBUG"));
                extraProps.forEach((k, v) -> {
                    cmd.add("-D" + k + "=" + k);
                });
                cmd.add("-jar");
                cmd.add(path);
                int timeout = executor.execute(cmd, appEnv);
                return timeout == 0;
            } catch (Exception e) {
                LOGGER.error("Failed to start registry (could not find runner JAR).", e);
                System.exit(1);
                return false;
            }
        }, runnable -> new Thread(runnable).start());
        processes.add(new RegistryTestProcess() {

            @Override
            public String getName() {
                return "registry";
            }

            @Override
            public void close() throws Exception {
                executor.stop();
            }

            @Override
            public String getStdOut() {
                return executor.stdOut();
            }

            @Override
            public String getStdErr() {
                return executor.stdErr();
            }

        });
    }

    private String findInMemoryRunner() {
        LOGGER.info("Attempting to find runner. Starting at cwd: " + new File("").getAbsolutePath());
        File appModuleDir = findAppModuleDir();
        File targetDir = new File(appModuleDir, "target");
        if (targetDir.isDirectory()) {
            File[] files = targetDir.listFiles();
            for (File file : files) {
                if (file.getName().contains("-runner") && file.getName().endsWith(".jar")) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private File findAppModuleDir() {
        File file = new File("../app");
        if (file.isDirectory()) {
            return file;
        }
        file = new File("./app");
        if (file.isDirectory()) {
            return file;
        }
        return null;
    }

    private boolean runnerExists(String path) throws IOException {
        if (path == null) {
            return false;
        }
        File file = new File(path);
        return file.isFile();
    }

    private String getJarPath() throws IOException {
        String path = REGISTRY_JAR_PATH;
        LOGGER.info("Checking runner JAR path (1): " + path);
        if (!runnerExists(path)) {
            if (PROJECT_VERSION != null) {
                path = String.format(REGISTRY_JAR_PATH_FORMAT, REGISTRY_STORAGE, REGISTRY_STORAGE, PROJECT_VERSION);
                LOGGER.info("Checking runner JAR path (2): " + path);
            }
        }
        if (!runnerExists(path)) {
            path = findInMemoryRunner();
            LOGGER.info("Checking runner JAR path (3): " + path);
        }
        if (!runnerExists(path)) {
            LOGGER.info("No runner JAR found.  Throwing an exception.");
            throw new IllegalStateException("Could not determine where to find the executable jar for the server. " +
                "This may happen if you are using an IDE to debug.");
        }
        return path;
    }

    public void stopAndCollectLogs(TestInfo testInfo) throws IOException {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String currentDate = simpleDateFormat.format(Calendar.getInstance().getTime());
        Path logsPath = Paths.get("target/logs/", REGISTRY_STORAGE.name(), testInfo.getTestClass().map(Class::getName).orElse(""),
                testInfo.getDisplayName());
        Files.createDirectories(logsPath);

        processes.descendingIterator().forEachRemaining(p -> {
            if (p.getName().equals("registry")) {
                try {
                    p.close();
                    Thread.sleep(3000);
                } catch (Exception e) {
                    LOGGER.error("error stopping registry", e);
                }
            }
            TestUtils.writeFile(logsPath.resolve(currentDate + "-" + p.getName() + "-" + "stdout.log"), p.getStdOut());
            String stdErr = p.getStdErr();
            if (stdErr != null && !stdErr.isEmpty()) {
                TestUtils.writeFile(logsPath.resolve(currentDate + "-" + p.getName() + "-" + "stderr.log"), stdErr);
            }
            if (!p.getName().equals("registry")) {
                try {
                    p.close();
                } catch (Exception e) {
                    LOGGER.error("error stopping registry", e);
                }
            }
        });
    }
}
