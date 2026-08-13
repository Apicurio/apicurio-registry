/*
 * Copyright 2026 Red Hat
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

package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ID;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Utils.handleError;

/**
 * MCP server for Rules-related operations.
 * Currently contains a tool for testing schema rules, but intended to eventually
 * cover all rules-related operations (list, create, delete rules).
 */
public class RulesMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Test schema validity and compatibility against an existing artifact in the Apicurio Registry. \
            This does not create a new version; it simulates doing so to verify if the provided \
            schema content passes all configured rules (validity, integrity, compatibility). \
            If it fails, it returns a detailed explanation of why it broke the rules.""")
    String test_schema_rules(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = "The raw content of the new version to test.") String versionContent,
            @ToolArg(description = "The content type of the new version (e.g. application/json).", required = false) String versionContentType
    ) {
        return handleError(() -> service.testSchemaRules(
                groupId,
                artifactId,
                versionContent,
                versionContentType
        ));
    }
}
