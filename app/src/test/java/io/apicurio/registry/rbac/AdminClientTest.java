package io.apicurio.registry.rbac;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.microsoft.kiota.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.RoleMapping;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RoleType;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UpdateRole;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ApplicationRbacEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ApplicationRbacEnabledProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AdminClientTest extends AbstractResourceTestBase {

    @Test
    public void smokeGlobalRules() throws Exception {
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");
        createGlobalRule(RuleType.VALIDITY, "FORWARD");

        TestUtils.retry(() -> {
            final List<RuleType> globalRules = clientV3.admin().rules().get();
            assertEquals(2, globalRules.size());
            assertTrue(globalRules.contains(RuleType.COMPATIBILITY));
            assertTrue(globalRules.contains(RuleType.VALIDITY));
        });
        clientV3.admin().rules().delete();
        TestUtils.retry(() -> {
            final List<RuleType> updatedRules = clientV3.admin().rules().get();
            assertEquals(0, updatedRules.size());
        });
    }

    @Test
    public void getGlobalRuleConfig() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            //Execution
            final Rule globalRuleConfig = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.getValue()).get();
            //Assertions
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });
    }

    @Test
    public void updateGlobalRuleConfig() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.getValue()).get();
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        final Rule toUpdate = new Rule();
        toUpdate.setType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FORWARD");

        //Execution
        final Rule updated = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.getValue()).put(toUpdate);

        //Assertions
        assertEquals(updated.getConfig(), "FORWARD");
    }

    @Test
    public void deleteGlobalRule() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.getValue()).get();
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        //Execution
        clientV3.admin().rules().byRule(RuleType.COMPATIBILITY.getValue()).delete();

        TestUtils.retry(() -> {
            final List<RuleType> ruleTypes = clientV3.admin().rules().get();

            //Assertions
            assertEquals(0, ruleTypes.size());
        });
    }

    @Test
    public void listArtifactTypes() throws Exception {
        final List<ArtifactTypeInfo> artifactTypes = clientV3.admin().artifactTypes().get();

        assertTrue(artifactTypes.size() > 0);
        assertTrue(artifactTypes.stream().anyMatch(t -> t.getName().equals("OPENAPI")));
        assertFalse(artifactTypes.stream().anyMatch(t -> t.getName().equals("UNKNOWN")));
    }

    @Test
    public void testRoleMappings() throws Exception {
        // Start with no role mappings
        List<RoleMapping> roleMappings = clientV3.admin().roleMappings().get();
        Assertions.assertTrue(roleMappings.isEmpty());

        // Add
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId("TestUser");
        mapping.setRole(RoleType.DEVELOPER);
        clientV3.admin().roleMappings().post(mapping);

        // Verify the mapping was added.
        TestUtils.retry(() -> {
            RoleMapping roleMapping = clientV3.admin().roleMappings().byPrincipalId("TestUser").get();
            Assertions.assertEquals("TestUser", roleMapping.getPrincipalId());
            Assertions.assertEquals(RoleType.DEVELOPER, roleMapping.getRole());
        });
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = clientV3.admin().roleMappings().get();
            Assertions.assertEquals(1, mappings.size());
            Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());
            Assertions.assertEquals(RoleType.DEVELOPER, mappings.get(0).getRole());
        });

        // Try to add the rule again - should get a 409
        TestUtils.retry(() -> {
            var exception = Assertions.assertThrows(ApiException.class, () -> {
                clientV3.admin().roleMappings().post(mapping);
            });
            assertEquals(409, exception.getResponseStatusCode());
        });

        // Add another mapping
        mapping.setPrincipalId("TestUser2");
        mapping.setRole(RoleType.ADMIN);
        clientV3.admin().roleMappings().post(mapping);

        // Get the list of mappings (should be 2 of them)
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = clientV3.admin().roleMappings().get();
            Assertions.assertEquals(2, mappings.size());
        });

        // Get a single mapping by principal
        RoleMapping tu2Mapping = clientV3.admin().roleMappings().byPrincipalId("TestUser2").get();
        Assertions.assertEquals("TestUser2", tu2Mapping.getPrincipalId());
        Assertions.assertEquals(RoleType.ADMIN, tu2Mapping.getRole());

        // Update a mapping
        UpdateRole ur = new UpdateRole();
        ur.setRole(RoleType.READ_ONLY);
        clientV3.admin().roleMappings().byPrincipalId("TestUser").put(ur);

        // Get a single (updated) mapping
        TestUtils.retry(() -> {
            RoleMapping tum = clientV3.admin().roleMappings().byPrincipalId("TestUser").get();
            Assertions.assertEquals("TestUser", tum.getPrincipalId());
            Assertions.assertEquals(RoleType.READ_ONLY, tum.getRole());
        });

        // Try to update a role mapping that doesn't exist
        var exception1 = Assertions.assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            UpdateRole ur2 = new UpdateRole();
            ur2.setRole(mapping.getRole());
            clientV3.admin().roleMappings().byPrincipalId("UnknownPrincipal").put(ur2);
        });
        assertEquals("RoleMappingNotFoundException", exception1.getName());

        // Delete a role mapping
        clientV3.admin().roleMappings().byPrincipalId("TestUser2").delete();

        // Get the (deleted) mapping by name (should fail with a 404)
        TestUtils.retry(() -> {
            var exception2 = Assertions.assertThrows(ApiException.class, () -> {
                clientV3.admin().roleMappings().byPrincipalId("TestUser2").get();
            });
            assertEquals(404, exception2.getResponseStatusCode());
        });

        // Get the list of mappings (should be 1 of them)
        TestUtils.retry(() -> {
            List<RoleMapping> mappings = clientV3.admin().roleMappings().get();
            Assertions.assertEquals(1, mappings.size());
            Assertions.assertEquals("TestUser", mappings.get(0).getPrincipalId());
        });

        // Clean up
        clientV3.admin().roleMappings().byPrincipalId("TestUser").delete();
    }

    protected Rule createGlobalRule(RuleType ruleType, String ruleConfig) throws ExecutionException, InterruptedException, TimeoutException {
        final Rule rule = new Rule();
        rule.setConfig(ruleConfig);
        rule.setType(ruleType);
        clientV3.admin().rules().post(rule);

        return rule;
    }
}
