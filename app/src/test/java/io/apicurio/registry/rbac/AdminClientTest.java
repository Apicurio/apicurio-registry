/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.exception.RoleMappingAlreadyExistsException;
import io.apicurio.registry.rest.client.exception.RoleMappingNotFoundException;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.LogLevel;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApplicationRbacEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author Jonathan Hughes 'jonathan.hughes@ibm.com'
 */

@QuarkusTest
@TestProfile(ApplicationRbacEnabledProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AdminClientTest extends AbstractResourceTestBase {

    @Test
    public void smokeGlobalRules() throws Exception {
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");
        createGlobalRule(RuleType.VALIDITY, "FORWARD");

        TestUtils.retry(() -> {
            final List<RuleType> globalRules = adminClientV2.listGlobalRules();
            assertEquals(2, globalRules.size());
            assertTrue(globalRules.contains(RuleType.COMPATIBILITY));
            assertTrue(globalRules.contains(RuleType.VALIDITY));
        });
        adminClientV2.deleteAllGlobalRules();
        TestUtils.retry(() -> {
            final List<RuleType> updatedRules = adminClientV2.listGlobalRules();
            assertEquals(0, updatedRules.size());
        });
    }

    @Test
    public void getGlobalRuleConfig() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            //Execution
            final Rule globalRuleConfig = adminClientV2.getGlobalRuleConfig(RuleType.COMPATIBILITY);
            //Assertions
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });
    }

    @Test
    public void updateGlobalRuleConfig() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = adminClientV2.getGlobalRuleConfig(RuleType.COMPATIBILITY);
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        final Rule toUpdate = new Rule();
        toUpdate.setType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FORWARD");

        //Execution
        final Rule updated = adminClientV2.updateGlobalRuleConfig(RuleType.COMPATIBILITY, toUpdate);

        //Assertions
        assertEquals(updated.getConfig(), "FORWARD");
    }

    @Test
    public void deleteGlobalRule() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = adminClientV2.getGlobalRuleConfig(RuleType.COMPATIBILITY);
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        //Execution
        adminClientV2.deleteGlobalRule(RuleType.COMPATIBILITY);

        TestUtils.retry(() -> {
            final List<RuleType> ruleTypes = adminClientV2.listGlobalRules();

            //Assertions
            assertEquals(0, ruleTypes.size());
        });
    }

    @Test
    public void smokeLogLevels() {
        final String logger = "smokeLogLevels";
        final List<NamedLogConfiguration> namedLogConfigurations = adminClientV2.listLogConfigurations();
        assertEquals(0, namedLogConfigurations.size());

        setLogLevel(logger, LogLevel.DEBUG);
        final NamedLogConfiguration logConfiguration = adminClientV2.getLogConfiguration(logger);
        assertEquals(LogLevel.DEBUG, logConfiguration.getLevel());
        assertEquals(logger, logConfiguration.getName());

        final List<NamedLogConfiguration> logConfigurations = adminClientV2.listLogConfigurations();
        assertEquals(1, logConfigurations.size());

        adminClientV2.removeLogConfiguration(logger);
    }

    @Test
    public void testRoleMappings() throws Exception {
        // Start with no role mappings
        List<RoleMapping> roleMappings = adminClientV2.listRoleMappings();
        Assertions.assertTrue(roleMappings.isEmpty());

        // Add
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId("TestUser");
        mapping.setRole(RoleType.DEVELOPER);
        adminClientV2.createRoleMapping(mapping);

        // Verify the mapping was added.
        TestUtils.retry(() -> {
            RoleMapping roleMapping = adminClientV2.getRoleMapping("TestUser");
            Assertions.assertEquals("TestUser", roleMapping.getPrincipalId());
            Assertions.assertEquals(RoleType.DEVELOPER, roleMapping.getRole());
        });
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = adminClientV2.listRoleMappings();
            Assertions.assertEquals(1, mappings.size());
            Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());
            Assertions.assertEquals(RoleType.DEVELOPER, mappings.get(0).getRole());
        });

        // Try to add the rule again - should get a 409
        TestUtils.retry(() -> {
            Assertions.assertThrows(RoleMappingAlreadyExistsException.class, () -> {
                adminClientV2.createRoleMapping(mapping);
            });
        });

        // Add another mapping
        mapping.setPrincipalId("TestUser2");
        mapping.setRole(RoleType.ADMIN);
        adminClientV2.createRoleMapping(mapping);

        // Get the list of mappings (should be 2 of them)
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = adminClientV2.listRoleMappings();
            Assertions.assertEquals(2, mappings.size());
        });

        // Get a single mapping by principal
        RoleMapping tu2Mapping = adminClientV2.getRoleMapping("TestUser2");
        Assertions.assertEquals("TestUser2", tu2Mapping.getPrincipalId());
        Assertions.assertEquals(RoleType.ADMIN, tu2Mapping.getRole());

        // Update a mapping
        adminClientV2.updateRoleMapping("TestUser", RoleType.READ_ONLY);

        // Get a single (updated) mapping
        TestUtils.retry(() -> {
            RoleMapping tum = adminClientV2.getRoleMapping("TestUser");
            Assertions.assertEquals("TestUser", tum.getPrincipalId());
            Assertions.assertEquals(RoleType.READ_ONLY, tum.getRole());
        });

        // Try to update a role mapping that doesn't exist
        Assertions.assertThrows(RoleMappingNotFoundException.class, () -> {
            adminClientV2.updateRoleMapping("UnknownPrincipal", RoleType.ADMIN);
        });

        // Delete a role mapping
        adminClientV2.deleteRoleMapping("TestUser2");

        // Get the (deleted) mapping by name (should fail with a 404)
        TestUtils.retry(() -> {
            Assertions.assertThrows(RoleMappingNotFoundException.class, () -> {
                adminClientV2.getRoleMapping("TestUser2");
            });
        });

        // Get the list of mappings (should be 1 of them)
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = adminClientV2.listRoleMappings();
            Assertions.assertEquals(1, mappings.size());
            Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());
        });

        // Clean up
        adminClientV2.deleteRoleMapping("TestUser");

    }

    private void setLogLevel(String log, LogLevel logLevel) {
        final LogConfiguration logConfiguration = new LogConfiguration();
        logConfiguration.setLevel(logLevel);
        adminClientV2.setLogConfiguration(log, logConfiguration);
    }

    private Rule createGlobalRule(RuleType ruleType, String ruleConfig) {
        final Rule rule = new Rule();
        rule.setConfig(ruleConfig);
        rule.setType(ruleType);
        adminClientV2.createGlobalRule(rule);

        return rule;
    }
}
