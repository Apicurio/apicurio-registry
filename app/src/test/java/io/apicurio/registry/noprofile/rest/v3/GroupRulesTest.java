package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.Error;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.utils.tests.DeletionEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
@TestProfile(DeletionEnabledProfile.class)
public class GroupRulesTest extends AbstractResourceTestBase {

    @Test
    public void testGroupRules() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Add a rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Verify the rule was added
        Rule rule = clientV3.groups().byGroupId(groupId).rules().byRuleType(RuleType.VALIDITY.name()).get();
        Assertions.assertEquals(ValidityLevel.FULL.name(), rule.getConfig());

        // Try to add the rule again - should get a 409
        Error error = Assertions.assertThrows(Error.class, () -> {
            CreateRule cr = new CreateRule();
            cr.setRuleType(RuleType.VALIDITY);
            cr.setConfig(ValidityLevel.FULL.name());
            clientV3.groups().byGroupId(groupId).rules().post(cr);
        });
        Assertions.assertEquals("A rule named 'VALIDITY' already exists.", error.getMessageEscaped());

        // Add another rule
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Verify the rule was added
        rule = clientV3.groups().byGroupId(groupId).rules().byRuleType(RuleType.COMPATIBILITY.name()).get();
        Assertions.assertEquals(CompatibilityLevel.BACKWARD.name(), rule.getConfig());

        // Get the list of rules (should be 2 of them)
        List<RuleType> rules = clientV3.groups().byGroupId(groupId).rules().get();
        rules.sort((r1, r2) -> r1.name().compareToIgnoreCase(r2.name()));
        Assertions.assertEquals(2, rules.size());
        Assertions.assertEquals(List.of(RuleType.COMPATIBILITY, RuleType.VALIDITY), rules);

        // Update a rule's config
        Rule updateRule = new Rule();
        updateRule.setRuleType(RuleType.COMPATIBILITY);
        updateRule.setConfig("FULL");
        rule = clientV3.groups().byGroupId(groupId).rules().byRuleType(RuleType.COMPATIBILITY.name())
                .put(updateRule);
        Assertions.assertEquals(CompatibilityLevel.FULL.name(), rule.getConfig());

        // Get a single (updated) rule by name
        rule = clientV3.groups().byGroupId(groupId).rules().byRuleType(RuleType.COMPATIBILITY.name()).get();
        Assertions.assertEquals(CompatibilityLevel.FULL.name(), rule.getConfig());

        // Delete a rule
        clientV3.groups().byGroupId(groupId).rules().byRuleType(RuleType.COMPATIBILITY.name()).delete();

        // Get a single (deleted) rule by name (should fail with a 404)
        error = Assertions.assertThrows(Error.class, () -> {
            clientV3.groups().byGroupId(groupId).rules().byRuleType(RuleType.COMPATIBILITY.name()).get();
        });
        Assertions.assertEquals("No rule named 'COMPATIBILITY' was found.", error.getMessageEscaped());

        // Get the list of rules (should be 1 of them)
        rules = clientV3.groups().byGroupId(groupId).rules().get();
        Assertions.assertEquals(1, rules.size());
        Assertions.assertEquals(List.of(RuleType.VALIDITY), rules);

        // Delete all rules
        clientV3.groups().byGroupId(groupId).rules().delete();

        // Get the list of rules (no rules now)
        rules = clientV3.groups().byGroupId(groupId).rules().get();
        Assertions.assertEquals(0, rules.size());

        // Add a rule to a group that doesn't exist.
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        error = Assertions.assertThrows(Error.class, () -> {
            CreateRule cr = new CreateRule();
            cr.setRuleType(RuleType.VALIDITY);
            cr.setConfig(ValidityLevel.FULL.name());
            clientV3.groups().byGroupId("group-that-does-not-exist").rules().post(cr);
        });
        Assertions.assertEquals("No group 'group-that-does-not-exist' was found.", error.getMessageEscaped());
    }

}
