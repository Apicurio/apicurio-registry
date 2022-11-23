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
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.apicurio.tests.common.auth.JWKSMockServer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.executor.Exec;
import io.apicurio.tests.common.utils.RegistryUtils;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

public class RegistryFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(RegistryFacade.class);

    private static final String REGISTRY_JAR_PATH_FORMAT = "../../storage/%s/target/apicurio-registry-storage-%s-%s-runner.jar";
    private static final String REGISTRY_JAR_PATH = System.getenv().get("REGISTRY_JAR_PATH");
    private static final String PROJECT_VERSION = System.getProperty("project.version");

    private LinkedList<RegistryTestProcess> processes = new LinkedList<>();

    private KeycloakContainer keycloakContainer;
    private JWKSMockServer keycloakMock;

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

    public AuthServerInfo getAuthServerInfo() {
        AuthServerInfo info = new AuthServerInfo();
        info.setAuthServerUrl(getMandatoryExternalRegistryEnvVar("AUTH_SERVER_URL", keycloakContainer.getAuthServerUrl()));
        //info hardcoded in test-realm.json
        info.setRealm(getMandatoryExternalRegistryEnvVar("AUTH_REALM", "registry"));

        info.setAdminClientId(getMandatoryExternalRegistryEnvVar("AUTH_ADMIN_CLIENT_ID", "registry-api"));
        info.setAdminClientSecret(getMandatoryExternalRegistryEnvVar("AUTH_ADMIN_CLIENT_SECRET", "test1"));

        info.setDeveloperClientId(getMandatoryExternalRegistryEnvVar("AUTH_DEV_CLIENT_ID", "registry-api-dev"));
        info.setDeveloperClientSecret(getMandatoryExternalRegistryEnvVar("AUTH_DEV_CLIENT_SECRET", "test1"));

        info.setReadOnlyClientId(getMandatoryExternalRegistryEnvVar("AUTH_READONLY_CLIENT_ID", "registry-api-readonly"));
        info.setReadOnlyClientSecret(getMandatoryExternalRegistryEnvVar("AUTH_READONLY_CLIENT_SECRET", "test1"));
        return info;
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

    public List<String> getClusteredRegistryNodes() {
        int c2port = TestUtils.getRegistryPort() + 1;
        int c3port = c2port + 1;
        return Arrays.asList("http://localhost:" + TestUtils.getRegistryPort(), "http://localhost:" + c2port, "http://localhost:" + c3port);
    }

    public JWKSMockServer getMTOnlyKeycloakMock() {
        return this.keycloakMock;
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

        LOGGER.info("Deploying registry using storage {}, test profile {}", RegistryUtils.REGISTRY_STORAGE.name(), RegistryUtils.TEST_PROFILE);

        Map<String, String> appEnv = initRegistryAppEnv();

        if (RegistryUtils.TEST_PROFILE.contains(Constants.MIGRATION)) {
            Map<String, String> registry1Env = new HashMap<>(appEnv);
            deployStorage(registry1Env);
            runRegistry(registry1Env, "node-1", String.valueOf(TestUtils.getRegistryPort()));
            Map<String, String> registry2Env = new HashMap<>(appEnv);
            deployStorage(registry2Env);
            runRegistry(registry2Env, "node-2", String.valueOf(TestUtils.getRegistryPort() + 1));

        } else if (Constants.MULTITENANCY.equals(RegistryUtils.TEST_PROFILE)) {

            runMultitenancySetup(appEnv);

        } else {

            deployStorage(appEnv);

            if (RegistryUtils.TEST_PROFILE.contains(Constants.CLUSTERED)) {

                Map<String, String> node1Env = new HashMap<>(appEnv);
                runRegistry(node1Env, "node-1" ,String.valueOf(TestUtils.getRegistryPort()));

                int c2port = TestUtils.getRegistryPort() + 1;

                Map<String, String> node2Env = new HashMap<>(appEnv);
                runRegistry(node2Env, "node-2" ,String.valueOf(c2port));

                int c3port = c2port + 1;

                Map<String, String> node3Env = new HashMap<>(appEnv);
                runRegistry(node3Env, "node-3" ,String.valueOf(c3port));

            } else {
                if (Constants.AUTH.equals(RegistryUtils.TEST_PROFILE)) {
                    runKeycloak(appEnv);
                }

                runRegistry(appEnv, "default", "8081");
            }
        }

    }

    public Map<String, String> initRegistryAppEnv() {
        Map<String, String> appEnv = new HashMap<>();
        appEnv.put("LOG_LEVEL", "DEBUG");
        appEnv.put("REGISTRY_LOG_LEVEL", "TRACE");

        loadProvidedAppEnv(appEnv);

        return appEnv;
    }

    public void runMultitenancySetup(Map<String, String> appEnv) throws Exception {

        runMultitenancyInfra(appEnv);

        runRegistry(appEnv, "default", "8081");

    }

    public void runMultitenancyInfra(Map<String, String> appEnv) throws Exception {
        deployStorage(appEnv);

        appEnv.put("REGISTRY_ENABLE_MULTITENANCY", "true");
        appEnv.put("REGISTRY_MULTITENANCY_REAPER_EVERY", "3s");
        appEnv.put("REGISTRY_MULTITENANCY_REAPER_PERIOD_SECONDS", "5");

        //auth is always enabled in multitenancy tests
        runKeycloakMock(appEnv);

        runTenantManager(appEnv);

        TestUtils.waitFor("Cannot connect to Tenant Manager on " + this.tenantManagerUrl + " in timeout!",
                Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, () -> TestUtils.isReachable("localhost", 8585, "Tenant Manager"));

        TestUtils.waitFor("Tenant Manager reports is ready",
                Constants.POLL_INTERVAL, Duration.ofSeconds(25).toMillis(),
                () -> TestUtils.isReady(this.tenantManagerUrl, "/q/health/ready", false, "Tenant Manager"),
                () -> TestUtils.isReady(this.tenantManagerUrl, "/q/health/ready", true, "Tenant Manager"));
    }

    private void deployStorage(Map<String, String> appEnv) throws Exception {
        deployStorage(appEnv, RegistryUtils.REGISTRY_STORAGE);
    }

    public void deployStorage(Map<String, String> appEnv, RegistryStorageType storage) throws Exception {
        switch (storage) {
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

        ThrowingConsumer<Integer> nodeIsReady = (port) -> {
            TestUtils.waitFor("Cannot connect to registries on node :" + port + " in timeout!",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, () -> TestUtils.isReachable("localhost", port, "registry node"));

            TestUtils.waitFor("Registry reports is ready",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_READY,
                    () -> TestUtils.isReady("http://localhost:" + port, "/health/ready", false, "registry node"),
                    () -> TestUtils.isReady("http://localhost:" + port, "/health/ready", true, "registry node"));
        };

        if (RegistryUtils.TEST_PROFILE.contains(Constants.CLUSTERED)) {

            try {
                nodeIsReady.accept(TestUtils.getRegistryPort());
                int c2port = TestUtils.getRegistryPort() + 1;
                nodeIsReady.accept(c2port);
                int c3port = c2port + 1;
                nodeIsReady.accept(c3port);
            } catch (Throwable e) {
                throw new Exception(e);
            }

        } if (RegistryUtils.TEST_PROFILE.contains(Constants.MIGRATION)) {

            try {
                nodeIsReady.accept(TestUtils.getRegistryPort());
                int c2port = TestUtils.getRegistryPort() + 1;
                nodeIsReady.accept(c2port);
            } catch (Throwable e) {
                throw new Exception(e);
            }

        } else {
            TestUtils.waitFor("Cannot connect to registries on " + TestUtils.getRegistryV1ApiUrl() + " in timeout!",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_START_UP, TestUtils::isReachable);

            TestUtils.waitFor("Registry reports is ready",
                    Constants.POLL_INTERVAL, Constants.TIMEOUT_FOR_REGISTRY_READY, () -> TestUtils.isReady(false), () -> TestUtils.isReady(true));
        }

    }

    private void runTenantManager(Map<String, String> registryAppEnv) throws Exception {
        Map<String, String> appEnv = new HashMap<>();
        appEnv.put("DATASOURCE_URL", registryAppEnv.get("REGISTRY_DATASOURCE_URL"));
        appEnv.put("DATASOURCE_USERNAME", registryAppEnv.get("REGISTRY_DATASOURCE_USERNAME"));
        appEnv.put("DATASOURCE_PASSWORD", registryAppEnv.get("REGISTRY_DATASOURCE_PASSWORD"));

        //auth is always enabled in multitenancy tests
        appEnv.put("AUTH_ENABLED", "true");
        appEnv.put("KEYCLOAK_URL", registryAppEnv.get("KEYCLOAK_URL"));
        appEnv.put("KEYCLOAK_REALM", registryAppEnv.get("KEYCLOAK_REALM"));
        appEnv.put("KEYCLOAK_API_CLIENT_ID", registryAppEnv.get("KEYCLOAK_API_CLIENT_ID"));
        appEnv.put("QUARKUS_OIDC_TLS_VERIFICATION", "none");

        //config only for test purposes
        //for TenantReaperIT , to enable tenant status transition from DELETED to READY
        appEnv.put("ENABLE_TEST_STATUS_TRANSITION", "true");

        appEnv.put("REGISTRY_ROUTE_URL", TestUtils.getRegistryBaseUrl());
        appEnv.put("LOG_LEVEL", "DEBUG");

        var nativeExec = getTenantManagerNativeExecutablePath();

        Exec executor = new Exec();
        if (nativeExec.isPresent()) {
            LOGGER.info("Starting Tenant Manager Native Executable app from: {}", nativeExec.get());
            CompletableFuture.supplyAsync(() -> {
                try {

                    List<String> cmd = new ArrayList<>();
                    cmd.add(nativeExec.get());
                    int timeout = executor.execute(cmd, appEnv);
                    return timeout == 0;
                } catch (Exception e) {
                    LOGGER.error("Failed to start native tenant-manager.", e);
                    System.exit(1);
                    return false;
                }
            }, runnable -> new Thread(runnable).start());
        } else {
            String path = getTenantManagerJarPath();
            LOGGER.info("Starting Tenant Manager app from: {}", path);
            CompletableFuture.supplyAsync(() -> {
                try {

                    List<String> cmd = new ArrayList<>();
                    cmd.add("java");
                    cmd.addAll(Arrays.asList(
                            // "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                            "-jar", path));
                    int timeout = executor.execute(cmd, appEnv);
                    return timeout == 0;
                } catch (Exception e) {
                    LOGGER.error("Failed to start tenant manager (could not find runner JAR).", e);
                    System.exit(1);
                    return false;
                }
            }, runnable -> new Thread(runnable).start());
        }

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

    private void runKeycloakMock(Map<String, String> appEnv) throws Exception {
        keycloakMock = new JWKSMockServer();
        keycloakMock.start();

        appEnv.put("AUTH_ENABLED", "true");
        appEnv.put("ROLE_BASED_AUTHZ_ENABLED", "true");
        appEnv.put("ROLE_BASED_AUTHZ_SOURCE", "application");

        appEnv.put("KEYCLOAK_URL", keycloakMock.authServerUrl);
        appEnv.put("KEYCLOAK_REALM", keycloakMock.realm);
        appEnv.put("KEYCLOAK_API_CLIENT_ID", keycloakMock.clientId);
        appEnv.put("QUARKUS_OIDC_TLS_VERIFICATION", "none");

        appEnv.put("TENANT_MANAGER_AUTH_URL", keycloakMock.authServerUrl);
        appEnv.put("TENANT_MANAGER_REALM", keycloakMock.realm);
        appEnv.put("TENANT_MANAGER_CLIENT_ID", keycloakMock.clientId);
        appEnv.put("TENANT_MANAGER_CLIENT_SECRET", keycloakMock.clientSecret);

        processes.add(new RegistryTestProcess() {

            @Override
            public String getName() {
                return "keycloak-mock";
            }

            @Override
            public void close() throws Exception {
                keycloakMock.stop();
            }

            @Override
            public String getStdOut() {
                return "";
            }

            @Override
            public String getStdErr() {
                return "";
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
        appEnv.put("ROLE_BASED_AUTHZ_ENABLED", "true");

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
    public void setupSQLStorage(Map<String, String> appEnv) throws Exception {

        String noDocker = System.getenv(Constants.NO_DOCKER_ENV_VAR);
        String currentEnv = System.getenv("CURRENT_ENV");

        if (currentEnv != null && "mas".equals(currentEnv)) {

            //postgresql running in a pre-deployed container

            appEnv.put("REGISTRY_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/test");
            appEnv.put("REGISTRY_DATASOURCE_USERNAME", "test");
            appEnv.put("REGISTRY_DATASOURCE_PASSWORD", "test");

            processes.add(new RegistryTestProcess() {

                @Override
                public String getName() {
                    return "container-postgresql";
                }

                @Override
                public void close() throws Exception {
                }

                @Override
                public String getStdOut() {
                    return "";
                }

                @Override
                public String getStdErr() {
                    return "";
                }

                @Override
                public boolean isContainer() {
                    return false;
                }
            });

        } else if (noDocker != null && noDocker.equals("true")) {
            EmbeddedPostgres database = EmbeddedPostgres.start();


            String datasourceUrl = database.getJdbcUrl("postgres", "postgres");

            appEnv.put("REGISTRY_DATASOURCE_URL", datasourceUrl);
            appEnv.put("REGISTRY_DATASOURCE_USERNAME", "postgres");
            appEnv.put("REGISTRY_DATASOURCE_PASSWORD", "postgres");
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
                    return "";
                }

                @Override
                public String getStdErr() {
                    return "";
                }

                @Override
                public boolean isContainer() {
                    return false;
                }
            });

        } else {

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

    }

    private void setupKafkaStorage(Map<String, String> appEnv) throws TimeoutException, InterruptedException, ExecutionException {

        if (RegistryUtils.TEST_PROFILE.contains(Constants.MIGRATION)) {
            KafkaFacade kafkaFacade = KafkaFacade.getInstance();
            var c = kafkaFacade.startNewKafka();

            appEnv.put("REGISTRY_KAFKASQL_CONSUMER_STARTUPLAG", "3000"); //Three seconds startup lag to give time to the nodes being created to create the db.
            appEnv.put("KAFKA_BOOTSTRAP_SERVERS", c.getBootstrapServers());
            processes.add(new RegistryTestProcess() {

                @Override
                public String getName() {
                    return "kafka-" + c.getContainerId();
                }

                @Override
                public void close() throws Exception {
                    c.close();
                }

                @Override
                public String getStdOut() {
                    return c.getLogs(OutputType.STDOUT);
                }

                @Override
                public String getStdErr() {
                    return c.getLogs(OutputType.STDERR);
                }

                @Override
                public boolean isContainer() {
                    return true;
                }
            });

            return;
        }

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

    public void runRegistry(Map<String, String> appEnv, String nameSuffix, String port) throws IOException {
        appEnv.put("QUARKUS_HTTP_PORT", port);
        if (RegistryUtils.DEPLOY_NATIVE_IMAGES.equals("true")) {
            runRegistryNativeImage(appEnv, nameSuffix);
            return;
        }
        String path = getJarPath();
        Exec executor = new Exec();
        LOGGER.info("Starting Registry app from: {} env: {}", path, appEnv);
        CompletableFuture.supplyAsync(() -> {
            try {

                List<String> cmd = new ArrayList<>();
                cmd.add("java");
                cmd.addAll(Arrays.asList(
                        // "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
//                        "-Dquarkus.http.port=8081",
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
                return "registry-" + nameSuffix;
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

    public void runContainer(Map<String, String> appEnv, String name, GenericContainer container) {
        container.withEnv(appEnv);
        container.start();
        processes.add(new RegistryTestProcess() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public void close() throws Exception {
                container.stop();
            }

            @Override
            public String getStdOut() {
                return container.getLogs(OutputType.STDOUT);
            }

            @Override
            public String getStdErr() {
                return container.getLogs(OutputType.STDERR);
            }

            @Override
            public boolean isContainer() {
                return true;
            }

        });
    }

    public void stopProcess(Path logsPath, String name) throws Exception {
        var process = processes.stream()
            .filter(p -> p.getName().equals(name))
            .findFirst()
            .get();
        stopAndCollectProcessLogs(logsPath, process);
        processes.remove(process);
    }

    private void runRegistryNativeImage(Map<String, String> appEnv, String nameSuffix) throws IOException {
        String path = getNativeExecutablePath();
        Exec executor = new Exec();
        LOGGER.info("Starting Registry Native Executable app from: {}", path);
        CompletableFuture.supplyAsync(() -> {
            try {

                List<String> cmd = new ArrayList<>();
                cmd.add(path);
                int timeout = executor.execute(cmd, appEnv);
                return timeout == 0;
            } catch (Exception e) {
                LOGGER.error("Failed to start registry.", e);
                System.exit(1);
                return false;
            }
        }, runnable -> new Thread(runnable).start());
        processes.add(new RegistryTestProcess() {

            @Override
            public String getName() {
                return "registry-native-" + nameSuffix;
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

    private String findTenantManagerJar() {
        LOGGER.info("Attempting to find tenant manager runner. Starting at cwd: " + new File("").getAbsolutePath());
        return findRunner(findTenantManagerModuleDir(), "jar");
    }

    private String findInMemoryRunner() {
        LOGGER.info("Attempting to find runner. Starting at cwd: " + new File("").getAbsolutePath());
        return findRunner(findAppModuleDir(), "jar");
    }

    private String findRunner(File mavenModuleDir, String extension) {
        File targetDir = new File(mavenModuleDir, "target");
        if (targetDir.isDirectory()) {
            File[] files = targetDir.listFiles();
            for (File file : files) {
                if (extension != null) {
                    if (file.getName().contains("-runner") && file.getName().endsWith("." + extension)) {
                        return file.getAbsolutePath();
                    }
                } else if (file.getName().endsWith("-runner")) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private File findTenantManagerModuleDir() {
        File file = new File("../../multitenancy/api");
        if (file.isDirectory()) {
            return file;
        }
        file = new File("../multitenancy/api");
        if (file.isDirectory()) {
            return file;
        }
        file = new File("./multitenancy/api");
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

    private String getNativeExecutablePath() throws IOException {
        String execPath;
        String path;
        if (RegistryStorageType.inmemory.equals(RegistryUtils.REGISTRY_STORAGE)) {
            execPath = "../../app/target/apicurio-registry-app-%s-runner";
            path = String.format(execPath, PROJECT_VERSION);
        } else {
            execPath = "../../storage/%s/target/apicurio-registry-storage-%s-%s-runner";
            path = String.format(execPath, RegistryUtils.REGISTRY_STORAGE, RegistryUtils.REGISTRY_STORAGE, PROJECT_VERSION);
        }
        if (!runnerExists(path)) {
            LOGGER.info("No runner JAR found.  Throwing an exception.");
            throw new IllegalStateException("Could not determine where to find the executable jar for the server. " +
                "This may happen if you are using an IDE to debug.");
        }
        return path;
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

    private Optional<String> getTenantManagerNativeExecutablePath() throws IOException {
        String path = findRunner(findTenantManagerModuleDir(), null);
        if (runnerExists(path)) {
            return Optional.of(path);
        }
        return Optional.empty();
    }

    private String getTenantManagerJarPath() throws IOException {
        String path = findTenantManagerJar();
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
        if (logsPath != null) {
            Files.createDirectories(logsPath);
        }

        processes.descendingIterator().forEachRemaining(p -> {
            stopAndCollectProcessLogs(logsPath, p);
        });
        processes.clear();
    }

    private void stopAndCollectProcessLogs(Path logsPath, RegistryTestProcess p) {
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
            final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String currentDate = simpleDateFormat.format(Calendar.getInstance().getTime());

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
    }

    private String getMandatoryExternalRegistryEnvVar(String envVar, String localValue) {
        if (TestUtils.isExternalRegistry()) {
            String var = System.getenv().get(envVar);
            if (var == null) {
                throw new IllegalStateException("missing " + envVar + " env var");
            }
            return var;
        } else {
            return localValue;
        }
    }

    /**
     * Reads environment variables with the prefix TEST_APP_ENV
     */
    private void loadProvidedAppEnv(Map<String, String> appEnv) {

        String envPrefix = "TEST_APP_ENV_";

        System.getenv()
            .entrySet()
            .stream()
            .filter(env -> env.getKey().startsWith(envPrefix))
            .forEach(env -> {
                appEnv.put(env.getKey().substring(envPrefix.length()), env.getValue());
            });

    }

}