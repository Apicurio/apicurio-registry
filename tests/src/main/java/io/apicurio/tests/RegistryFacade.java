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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.executor.Exec;
import io.apicurio.tests.utils.RegistryUtils;

public class RegistryFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(RegistryFacade.class);

    private static final String REGISTRY_JAR_PATH_FORMAT = "../storage/%s/target/apicurio-registry-storage-%s-%s-runner.jar";
    private static final String REGISTRY_JAR_PATH = System.getenv().get("REGISTRY_JAR_PATH");
    private static final String PROJECT_VERSION = System.getProperty("project.version");

    private LinkedList<RegistryTestProcess> processes = new LinkedList<>();

    private static RegistryFacade instance;

    public static RegistryFacade getInstance() {
        if (instance == null) {
            instance = new RegistryFacade();
        }
        return instance;
    }

    private RegistryFacade() {
        //hidden constructor, singleton class
    }

    public boolean isRunning() {
        return !processes.isEmpty();
    }

    /**
     * Method for starting the registry from a runner jar file. New process is created.
     */
    public void start() throws Exception {
        if (!processes.isEmpty()) {
            throw new IllegalStateException("Registry is already running");
        }

        if (RegistryUtils.REGISTRY_STORAGE == null) {
            throw new IllegalStateException("REGISTRY_STORAGE is mandatory, have you specified a profile with the storage to test?");
        }

        String path = getJarPath();
        if (path == null) {
            throw new IllegalStateException("Could not determine where to find the executable jar for the server. " +
                "This may happen if you are using an IDE to debug.");
        }
        LOGGER.info("Deploying registry using storage {}", RegistryUtils.REGISTRY_STORAGE.name());
        Map<String, String> appEnv = new HashMap<>();
        switch (RegistryUtils.REGISTRY_STORAGE) {
            case inmemory:
            case infinispan:
                break;
            case kafka:
            case streams:
                setupKafkaStorage(appEnv);
                break;
            case sql:
                setupSQLStorage(appEnv);
                break;
        }

        runRegistry(path, appEnv);

    }

    private void setupSQLStorage(Map<String, String> appEnv) throws Exception {
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

    private void setupKafkaStorage(Map<String, String> appEnv) throws TimeoutException, InterruptedException, ExecutionException {

        KafkaFacade kafkaFacade = KafkaFacade.getInstance();
        kafkaFacade.start();

        Properties properties = new Properties();
        properties.put("bootstrap.servers", kafkaFacade.bootstrapServers());
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

        appEnv.put("KAFKA_BOOTSTRAP_SERVERS", kafkaFacade.bootstrapServers());
        if (RegistryUtils.REGISTRY_STORAGE == RegistryStorageType.streams) {
            appEnv.put("APPLICATION_ID", "test-application");
        }
        processes.add(kafkaFacade);
    }

    private void runRegistry(String path, Map<String, String> appEnv) {
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
                        "-Dquarkus.log.category.\"io\".level=DEBUG",
                        "-jar", path));
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
                path = String.format(REGISTRY_JAR_PATH_FORMAT, RegistryUtils.REGISTRY_STORAGE, RegistryUtils.REGISTRY_STORAGE, PROJECT_VERSION);
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

    public void stopAndCollectLogs(Path logsPath) throws IOException {
        LOGGER.info("Stopping registry");
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String currentDate = simpleDateFormat.format(Calendar.getInstance().getTime());
        if (logsPath != null) {
            Files.createDirectories(logsPath);
        }

        processes.descendingIterator().forEachRemaining(p -> {
            //registry process is not a container and have to be stopped before being able to read log output
            if (p.getName().equals("registry")) {
                try {
                    p.close();
                    Thread.sleep(3000);
                } catch (Exception e) {
                    LOGGER.error("error stopping registry", e);
                }
            }
            if (logsPath != null) {
                Path filePath = logsPath.resolve(currentDate + "-" + p.getName() + "-" + "stdout.log");
                LOGGER.info("Storing registry logs to " + filePath.toString());
                TestUtils.writeFile(filePath, p.getStdOut());
                String stdErr = p.getStdErr();
                if (stdErr != null && !stdErr.isEmpty()) {
                    TestUtils.writeFile(logsPath.resolve(currentDate + "-" + p.getName() + "-" + "stderr.log"), stdErr);
                }
            }
            if (!p.getName().equals("registry")) {
                try {
                    p.close();
                } catch (Exception e) {
                    LOGGER.error("error stopping registry", e);
                }
            }
        });
        processes.clear();
    }

}