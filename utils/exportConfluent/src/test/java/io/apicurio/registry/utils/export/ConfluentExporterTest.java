package io.apicurio.registry.utils.export;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.RuleType;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

/**
 * Integration test for the Confluent Schema Registry export utility.
 *
 * This test uses Testcontainers to automatically start/stop:
 * - Confluent Schema Registry (with Kafka)
 * - Apicurio Registry
 *
 * Note: Set USE_EXTERNAL_APICURIO=true environment variable to use an external
 * Apicurio Registry instance instead of starting one with Testcontainers.
 */
public class ConfluentExporterTest {

    private static final Logger log = Logger.getLogger(ConfluentExporterTest.class);

    private static final String EXPORT_ZIP_FILE = "target/confluent-schema-registry-export.zip";
    private static final boolean USE_EXTERNAL_APICURIO = Boolean.parseBoolean(
            System.getenv().getOrDefault("USE_EXTERNAL_APICURIO", "false"));
    private static final String EXTERNAL_APICURIO_URL = System.getenv().getOrDefault(
            "APICURIO_URL", "http://localhost:8080/apis/registry/v3");

    private static Network network;
    private static KafkaContainer kafka;
    private static GenericContainer<?> schemaRegistry;
    private static GenericContainer<?> apicurioRegistry;
    private static String confluentUrl;
    private static String apicurioUrl;

    private static SchemaRegistryClient confluentClient;
    private static RegistryClient apicurioClient;

    @BeforeAll
    public static void startContainers() throws Exception {
        log.info("=================================================================");
        log.info("Starting Testcontainers...");
        log.info("=================================================================");

        // Create network
        network = Network.newNetwork();

        // Start Kafka
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withNetwork(network)
                .withNetworkAliases("kafka");
        kafka.start();

        log.info("Kafka started at: " + kafka.getBootstrapServers());

        // Start Confluent Schema Registry
        schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0"))
                .withNetwork(network)
                .withExposedPorts(8081)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "kafka:9092")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081");
        schemaRegistry.start();

        confluentUrl = "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
        log.info("Confluent Schema Registry started at: " + confluentUrl);

        // Start Apicurio Registry (or use external instance)
        if (USE_EXTERNAL_APICURIO) {
            apicurioUrl = EXTERNAL_APICURIO_URL;
            log.info("Using external Apicurio Registry at: " + apicurioUrl);
        } else {
            apicurioRegistry = new GenericContainer<>(DockerImageName.parse("quay.io/apicurio/apicurio-registry:latest-snapshot"))
                    .withNetwork(network)
                    .withExposedPorts(8080)
                    .withEnv("QUARKUS_PROFILE", "prod")
                    .withEnv("APICURIO_STORAGE_KIND", "sql")
                    .withEnv("APICURIO_STORAGE_SQL_KIND", "h2");
            apicurioRegistry.start();

            String host = apicurioRegistry.getHost();
            Integer port = apicurioRegistry.getMappedPort(8080);
            apicurioUrl = "http://" + host + ":" + port + "/apis/registry/v3";
            log.info("Apicurio Registry started at: " + apicurioUrl);

            // Wait for Apicurio Registry to be ready
            waitForApicurioReady(apicurioUrl);
        }

        // Create clients
        RestService restService = new RestService(confluentUrl);
        confluentClient = new CachedSchemaRegistryClient(restService, 64, Collections.emptyMap());
        RegistryClientOptions clientOptions = RegistryClientOptions.create(apicurioUrl);
        apicurioClient = RegistryClientFactory.create(clientOptions);

        log.info("=================================================================");
    }

    /**
     * Waits for Apicurio Registry to become ready by checking the health endpoint.
     */
    private static void waitForApicurioReady(String baseUrl) throws Exception {
        String healthUrl = baseUrl.replace("/apis/registry/v3", "/health/ready");
        log.info("Waiting for Apicurio Registry to be ready...");

        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(healthUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);

                if (conn.getResponseCode() == 200) {
                    log.info("Apicurio Registry is ready!");
                    return;
                }
            } catch (Exception e) {
                // Ignore and retry
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException("Apicurio Registry did not become ready in time");
    }

    @AfterAll
    public static void stopContainers() {
        log.info("=================================================================");
        log.info("Stopping Testcontainers...");
        log.info("=================================================================");

        if (apicurioRegistry != null) {
            apicurioRegistry.stop();
        }
        if (schemaRegistry != null) {
            schemaRegistry.stop();
        }
        if (kafka != null) {
            kafka.stop();
        }
        if (network != null) {
            network.close();
        }

        // Cleanup export file
        File exportFile = new File(EXPORT_ZIP_FILE);
        if (exportFile.exists()) {
            exportFile.delete();
        }
    }

    /**
     * Full integration test: populate Confluent SR → export → import to Apicurio → verify.
     */
    @Test
    public void testConfluentExportImportWorkflow() throws Exception {
        log.info("\n=================================================================");
        log.info("STEP 1: Populating Confluent Schema Registry");
        log.info("=================================================================");
        populateConfluentRegistry();

        log.info("\n=================================================================");
        log.info("STEP 2: Exporting from Confluent Schema Registry");
        log.info("=================================================================");
        exportFromConfluent();

        log.info("\n=================================================================");
        log.info("STEP 3: Importing into Apicurio Registry");
        log.info("=================================================================");
        File exportFile = new File(EXPORT_ZIP_FILE);
        Assertions.assertTrue(exportFile.exists(), "Export file should exist");
        importToApicurio(exportFile);

        log.info("\n=================================================================");
        log.info("STEP 4: Verifying imported data in Apicurio Registry");
        log.info("=================================================================");
        verifyImportedData();

        log.info("\n=================================================================");
        log.info("TEST PASSED!");
        log.info("=================================================================");
    }

    /**
     * Populates Confluent Schema Registry with test schemas.
     */
    private void populateConfluentRegistry() throws Exception {
        // Register 3 AVRO schemas
        String avroSchema1 = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"int\"}]}";
        String avroSchema2 = "{\"type\":\"record\",\"name\":\"Product\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"price\",\"type\":\"double\"}]}";
        String avroSchema3 = "{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"orderId\",\"type\":\"string\"},{\"name\":\"userId\",\"type\":\"string\"}]}";

        confluentClient.register("user-schema", new AvroSchema(avroSchema1));
        confluentClient.register("product-schema", new AvroSchema(avroSchema2));
        confluentClient.register("order-schema", new AvroSchema(avroSchema3));

        // Register 1 JSON schema
        String jsonSchema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}}}";
        confluentClient.register("contact-schema", new JsonSchema(jsonSchema));

        // Register 1 Protobuf schema
        String protoSchema = "syntax = \"proto3\";\nmessage Person {\n  string name = 1;\n  int32 id = 2;\n}";
        confluentClient.register("person-schema", new ProtobufSchema(protoSchema));

        // Set subject-level compatibility for one schema
        confluentClient.updateCompatibility("user-schema", "FULL");

        // Set global compatibility
        confluentClient.updateCompatibility(null, "BACKWARD");

        log.info("Registered 5 schemas in Confluent Schema Registry");
        log.info("Set global compatibility: BACKWARD");
        log.info("Set user-schema compatibility: FULL");
    }

    /**
     * Exports data from Confluent Schema Registry using the Export utility.
     */
    private void exportFromConfluent() throws Exception {
        Export exporter = new Export();

        // Initialize required dependencies
        exporter.log = org.jboss.logging.Logger.getLogger(Export.class);
        exporter.artifactReferenceMapper = new io.apicurio.registry.utils.export.mappers.ArtifactReferenceMapper();

        // Ensure target directory exists
        File targetDir = new File("target");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        int exitCode = exporter.run(confluentUrl, "--output", EXPORT_ZIP_FILE);
        Assertions.assertEquals(0, exitCode, "Export should complete successfully");

        File exportFile = new File(EXPORT_ZIP_FILE);
        log.info("Export file created: " + exportFile.getAbsolutePath());
        log.info("Export file size: " + exportFile.length() + " bytes");
    }

    /**
     * Imports the export zip file into Apicurio Registry.
     */
    private void importToApicurio(File exportFile) throws Exception {
        try (InputStream is = new FileInputStream(exportFile)) {
            apicurioClient.admin().importEscaped().post(is, config -> {
                config.headers.putIfAbsent("Content-Type", Set.of("application/zip"));
            });
        }
        log.info("Successfully imported data into Apicurio Registry");
    }

    /**
     * Verifies that the imported data is correct in Apicurio Registry.
     */
    private void verifyImportedData() throws Exception {
        // Verify artifacts were imported
        ArtifactSearchResults artifacts = apicurioClient.search().artifacts().get(config -> {
            config.queryParameters.limit = 100;
        });

        Assertions.assertNotNull(artifacts);
        Assertions.assertEquals(5, artifacts.getCount(), "Should have 5 artifacts imported");
        log.info("✓ Found 5 artifacts");

        // Verify specific artifacts exist with correct versions
        String[] expectedArtifacts = {"user-schema", "product-schema", "order-schema", "contact-schema", "person-schema"};
        for (String artifactId : expectedArtifacts) {
            ArtifactMetaData metadata = apicurioClient.groups().byGroupId("default")
                    .artifacts().byArtifactId(artifactId).get();
            Assertions.assertNotNull(metadata, "Artifact " + artifactId + " should exist");

            VersionMetaData versionMeta = apicurioClient.groups().byGroupId("default")
                    .artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").get();

            Assertions.assertEquals("1", versionMeta.getVersion(), "Version should be '1'");
            Assertions.assertTrue(versionMeta.getGlobalId() > 0, "Global ID should be positive");

            log.info("✓ Verified artifact: " + artifactId + " (globalId=" + versionMeta.getGlobalId() + ", version=1)");
        }

        // Verify global rule was imported
        Rule globalCompatibilityRule = apicurioClient.admin().rules().byRuleType(RuleType.COMPATIBILITY.name()).get();
        Assertions.assertNotNull(globalCompatibilityRule);
        Assertions.assertEquals("BACKWARD", globalCompatibilityRule.getConfig());
        log.info("✓ Global compatibility rule: BACKWARD");

        // Verify artifact-level rule was imported for user-schema
        Rule userSchemaRule = apicurioClient.groups().byGroupId("default")
                .artifacts().byArtifactId("user-schema")
                .rules().byRuleType(RuleType.COMPATIBILITY.name()).get();
        Assertions.assertNotNull(userSchemaRule);
        Assertions.assertEquals("FULL", userSchemaRule.getConfig());
        log.info("✓ user-schema compatibility rule: FULL");

        // Verify branches were created (latest branch should exist)
        var branches = apicurioClient.groups().byGroupId("default")
                .artifacts().byArtifactId("user-schema").branches().get();
        Assertions.assertNotNull(branches);
        Assertions.assertTrue(branches.getBranches().size() > 0, "Should have at least one branch");

        boolean latestBranchFound = branches.getBranches().stream()
                .anyMatch(b -> "latest".equals(b.getBranchId()));
        Assertions.assertTrue(latestBranchFound, "Latest branch should exist");
        log.info("✓ Latest branch exists for user-schema");

        log.info("\nAll verifications passed!");
    }
}