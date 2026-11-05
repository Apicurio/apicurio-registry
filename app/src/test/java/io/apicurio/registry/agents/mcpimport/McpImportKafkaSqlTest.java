package io.apicurio.registry.agents.mcpimport;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The same imports, rules and reports on KafkaSQL storage.
 */
@QuarkusTest
@TestProfile(McpImportKafkasqlTestProfile.class)
public class McpImportKafkaSqlTest extends McpImportTest {
}
