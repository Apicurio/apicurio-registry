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
package io.apicurio.tests.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
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
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.executor.Exec;
import io.apicurio.tests.common.utils.RegistryUtils;

public class RegistryFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(RegistryFacade.class);

    private static final String REGISTRY_JAR_PATH_FORMAT = "../../storage/%s/target/apicurio-registry-storage-%s-%s-runner.jar";
    private static final String REGISTRY_JAR_PATH = System.getenv().get("REGISTRY_JAR_PATH");
    private static final String PROJECT_VERSION = System.getProperty("project.version");

    private LinkedList<RegistryTestProcess> processes = new LinkedList<>();

    private KeycloakContainer keycloakContainer;

    private String tenantManagerUrl = "http://localhost:8585";

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

    public String getTenantManagerUrl() {
        return this.tenantManagerUrl;
    }

    public String getAuthServerUrl() {
        return keycloakContainer.getAuthServerUrl();
    }

    public Keycloak getKeycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakContainer.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .grantType("password")
                .username(keycloakContainer.getAdminUsername())
                .password(keycloakContainer.getAdminPassword())
                .build();
    }

    public String getSourceRegistryUrl() {
        if (TestUtils.isExternalRegistry()) {
            String host = System.getenv().get("SOURCE_REGISTRY_HOST");
            if (host == null) {
                throw new IllegalStateException("missing SOURCE_REGISTRY_HOST env var");
            }
            Integer port = Integer.parseInt(System.getenv().getOrDefault("SOURCE_REGISTRY_PORT", "0"));
            if (port == 0) {
                throw new IllegalStateException("missing SOURCE_REGISTRY_PORT env var");
            }
            return "http://" + host + ":" + port + "/apis/registry/v2";
        } else {
            return "http://localhost:" + TestUtils.getRegistryPort() + "/apis/registry/v2";
        }
    }

    public String getDestRegistryUrl() {
        if (TestUtils.isExternalRegistry()) {
            String host = System.getenv().get("DEST_REGISTRY_HOST");
            if (host == null) {
                throw new IllegalStateException("missing DEST_REGISTRY_HOST env var");
            }
            Integer port = Integer.parseInt(System.getenv().getOrDefault("DEST_REGISTRY_PORT", "0"));
            if (port == 0) {
                throw new IllegalStateException("missing DEST_REGISTRY_PORT env var");
            }
            return "http://" + host + ":" + port + "/apis/registry/v2";
        } else {
            int port = TestUtils.getRegistryPort() + 1;
            return "http://localhost:" + port + "/apis/registry/v2";
        }
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
            throw new IllegalStateException("REGISTRY_STORAGE is mandatory, have you specified a profile with the storage to test? is the class an integration test *IT.java ?");
        }

        if (RegistryUtils.REGISTRY_STORAGE != RegistryStorageType.sql && Constants.MULTITENANCY.equals(RegistryUtils.TEST_PROFILE)) {
            throw new IllegalStateException("Only sql storage allowed for multitenancy tests");
        }

        String path = getJarPath();
        if (path == null) {
            throw new IllegalStateException("Could not determine where to find the executable jar for the server. " +
                "This may happen if you are using an IDE to debug.");
        }
        LOGGER.info("Deploying registry using storage {}, test profile {}", RegistryUtils.REGISTRY_STORAGE.name(), RegistryUtils.TEST_PROFILE);
        Map<String, String> appEnv = new HashMap<>();
        appEnv.put("LOG_LEVEL", "DEBUG");
        appEnv.put("REGISTRY_LOG_LEVEL", "DEBUG");

        if (RegistryUtils.TEST_PROFILE.contains(Constants.MIGRATION)) {
            Map<String, String> registry1Env = new HashMap<>(appEnv);
            deployStorage(registry1Env);
            runRegistryNode(path, registry1Env, String.valueOf(TestUtils.getRegistryPort()));
            Map<String, String> registry2Env = new HashMap<>(appEnv);
            deployStorage(registry2Env);
            runRegistryNode(path, registry2Env, String.valueOf(TestUtils.getRegistryPort() + 1));
        } else {

            deployStorage(appEnv);

            if (RegistryUtils.TEST_PROFILE.contains(Constants.CLUSTERED)) {

                Map<String, String> node1Env = new HashMap<>(appEnv);
                runRegistryNode(path, node1Env, String.valueOf(TestUtils.getRegistryPort()));

                Map<String, String> node2Env = new HashMap<>(appEnv);
                runRegistryNode(path, node2Env, String.valueOf(TestUtils.getRegistryPort() + 1));

            } else {
                if (Constants.MULTITENANCY.equals(RegistryUtils.TEST_PROFILE)) {
                    appEnv.put("REGISTRY_ENABLE_MULTITENANCY", "true");
                    runKeycloak(appEnv);
                    runTenantManager(appEnv);
                }

                runRegistry(path, appEnv);
            }
        }

    }

    private void deployStorage(Map<String, String> appEnv) throws Exception {
        switch (RegistryUtils.REGISTRY_STORAGE) {
            case inmemory:
                break;
            case sql:
                setupSQLStorage(appEnv);
                break;
            case kafkasql:
                setupKafkaStorage(appEnv);
                break;
        }
    }

    public void waitForRegistryReady() throws Exception {

        if (RegistryUtils.TEST_PROFILE.contains(Constants.CLUSTERED)) {

            ThrowingConsumer<Integer> nodeIsReady = (port) -> {
                TestUtils.waitFor("Cannot connect to registries on node :" + port + " in timeout!",
                        Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, () -> TestUtils.isReachable("localhost", port, "registry node"));

                TestUtils.waitFor("Registry reports is ready",
                        Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_READY,
                        () -> TestUtils.isReady("http://localhost:" + port, "/health/ready", false, "registry node"),
                        () -> TestUtils.isReady("http://localhost:" + port, "/health/ready", true, "registry node"));
            };

            try {
                nodeIsReady.accept(TestUtils.getRegistryPort());
                nodeIsReady.accept(TestUtils.getRegistryPort() + 1);
            } catch (Throwable e) {
                throw new Exception(e);
            }
        } else {
            TestUtils.waitFor("Cannot connect to registries on " + TestUtils.getRegistryV1ApiUrl() + " in timeout!",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, TestUtils::isReachable);

            TestUtils.waitFor("Registry reports is ready",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_READY, () -> TestUtils.isReady(false), () -> TestUtils.isReady(true));

            if (Constants.MULTITENANCY.equals(RegistryUtils.TEST_PROFILE)) {
                TestUtils.waitFor("Cannot connect to Tenant Manager on " + this.tenantManagerUrl + " in timeout!",
                        Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, () -> TestUtils.isReachable("localhost", 8585, "Tenant Manager"));

                TestUtils.waitFor("Tenant Manager reports is ready",
                        Constants.POLL_INTERVAL, Duration.ofSeconds(25).toMillis(),
                        () -> TestUtils.isReady(this.tenantManagerUrl, "/q/health/ready", false, "Tenant Manager"),
                        () -> TestUtils.isReady(this.tenantManagerUrl, "/q/health/ready", true, "Tenant Manager"));
            }
        }

    }

    private void runRegistryNode(String path, Map<String, String> appEnv, String httpPort) {
        Exec executor = new Exec();
        LOGGER.info("Starting Registry app from: {}", path);
        CompletableFuture.supplyAsync(() -> {
            try {

                List<String> cmd = new ArrayList<>();
                cmd.add("java");
                cmd.addAll(Arrays.asList(
                        // "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                        "-Dquarkus.http.port=" + httpPort,
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
                return "registry-node" + httpPort;
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

            @Override
            public boolean isContainer() {
                return false;
            }

        });
    }

    private void runTenantManager(Map<String, String> registryAppEnv) throws Exception {
        Map<String, String> appEnv = new HashMap<>();
        appEnv.put("DATASOURCE_URL", registryAppEnv.get("REGISTRY_DATASOURCE_URL"));
        appEnv.put("DATASOURCE_USERNAME", registryAppEnv.get("REGISTRY_DATASOURCE_USERNAME"));
        appEnv.put("DATASOURCE_PASSWORD", registryAppEnv.get("REGISTRY_DATASOURCE_PASSWORD"));

        appEnv.put("REGISTRY_ROUTE_URL", TestUtils.getRegistryBaseUrl());

        Exec executor = new Exec();
        String path = getTenantManagerJarPath();
        LOGGER.info("Starting Tenant Manager app from: {}", path);
        CompletableFuture.supplyAsync(() -> {
            try {

                List<String> cmd = new ArrayList<>();
                cmd.add("java");
                cmd.addAll(Arrays.asList(
                        // "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                        "-Dquarkus.log.console.level=DEBUG",
                        "-Dquarkus.log.category.\"io\".level=DEBUG",
                        "-jar", path));
                int timeout = executor.execute(cmd, appEnv);
                return timeout == 0;
            } catch (Exception e) {
                LOGGER.error("Failed to start tenant manager (could not find runner JAR).", e);
                System.exit(1);
                return false;
            }
        }, runnable -> new Thread(runnable).start());
        processes.add(new RegistryTestProcess() {

            @Override
            public String getName() {
                return "tenant-manager";
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

            @Override
            public boolean isContainer() {
                return false;
            }

        });
    }

    private void runKeycloak(Map<String, String> appEnv) throws Exception {

        keycloakContainer = new KeycloakContainer()
                .withRealmImportFile("test-realm.json");
        keycloakContainer.start();
        TestUtils.waitFor("Keycloak is running",
                Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, keycloakContainer::isRunning);

        appEnv.put("AUTH_ENABLED", "true");
        appEnv.put("KEYCLOAK_URL", keycloakContainer.getAuthServerUrl());
        appEnv.put("KEYCLOAK_REALM", "registry");
        appEnv.put("KEYCLOAK_API_CLIENT_ID", "registry-api");
        appEnv.put("QUARKUS_OIDC_TLS_VERIFICATION", "none");

        processes.add(new RegistryTestProcess() {

            @Override
            public String getName() {
                return "keycloak";
            }

            @Override
            public void close() throws Exception {
                keycloakContainer.close();
            }

            @Override
            public String getStdOut() {
                return keycloakContainer.getLogs(OutputType.STDOUT);
            }

            @Override
            public String getStdErr() {
                return keycloakContainer.getLogs(OutputType.STDERR);
            }

            @Override
            public boolean isContainer() {
                return true;
            }

        });
    }

    @SuppressWarnings("rawtypes")
    private void setupSQLStorage(Map<String, String> appEnv) throws Exception {
        PostgreSQLContainer database = new PostgreSQLContainer<>("postgres:10.12");
        database.start();
        TestUtils.waitFor("Database is running",
                Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, database::isRunning);

        String datasourceUrl = "jdbc:postgresql://" + database.getContainerIpAddress() + ":" +
                database.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database.getDatabaseName();
        appEnv.put("REGISTRY_DATASOURCE_URL", datasourceUrl);
        appEnv.put("REGISTRY_DATASOURCE_USERNAME", database.getUsername());
        appEnv.put("REGISTRY_DATASOURCE_PASSWORD", database.getPassword());
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

            @Override
            public boolean isContainer() {
                return true;
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

            @Override
            public boolean isContainer() {
                return false;
            }

        });
    }

    private String findTenantManagerRunner() {
        LOGGER.info("Attempting to find tenant manager runner. Starting at cwd: " + new File("").getAbsolutePath());
        return findRunner(findTenantManagerModuleDir());
    }

    private String findInMemoryRunner() {
        LOGGER.info("Attempting to find runner. Starting at cwd: " + new File("").getAbsolutePath());
        return findRunner(findAppModuleDir());
    }

    private String findRunner(File mavenModuleDir) {
        File targetDir = new File(mavenModuleDir, "target");
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

    private File findTenantManagerModuleDir() {
        File file = new File("../../multitenancy/tenant-manager-api");
        if (file.isDirectory()) {
            return file;
        }
        file = new File("../multitenancy/tenant-manager-api");
        if (file.isDirectory()) {
            return file;
        }
        file = new File("./multitenancy/tenant-manager-api");
        if (file.isDirectory()) {
            return file;
        }
        return null;
    }

    private File findAppModuleDir() {
        File file = new File("../../app");
        if (file.isDirectory()) {
            return file;
        }
        file = new File("../app");
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

    private String getTenantManagerJarPath() throws IOException {
        String path = findTenantManagerRunner();
        LOGGER.info("Checking tenant manager runner JAR path: " + path);
        if (!runnerExists(path)) {
            LOGGER.info("No runner JAR found.  Throwing an exception.");
            throw new IllegalStateException("Could not determine where to find the executable jar for the tenant manager app. " +
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
            //registry and tenant manager processes are not a container and have to be stopped before being able to read log output
            if (!p.isContainer()) {
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