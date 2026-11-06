package io.apicurio.registry.serde;

import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.resolver.client.ContractRulesetCache;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractRuleLoaderTest {

    private final List<String> calls = new ArrayList<>();

    /** A facade answering the three ruleset lookups from fixed rulesets, recording each call. */
    private RegistryClientFacade facade(ContractRuleSet global, ContractRuleSet artifact,
            Map<String, ContractRuleSet> versions) {
        return (RegistryClientFacade) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RegistryClientFacade.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "getGlobalContractRuleset" -> global;
                        case "getContractRuleset" -> artifact;
                        case "getVersionContractRuleset" -> versions.get((String) args[2]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private static ContractRule rule(String name, String expr) {
        ContractRule rule = new ContractRule();
        rule.setName(name);
        rule.setType("CEL");
        rule.setExpr(expr);
        return rule;
    }

    private static ContractRuleSet domainRules(ContractRule... rules) {
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(rules));
        return ruleset;
    }

    @Test
    void testDomainRulesMergeScopesByNameNarrowestWins() {
        RegistryClientFacade client = facade(
                domainRules(rule("shared", "global"), rule("global-only", "g")),
                domainRules(rule("shared", "artifact"), rule("artifact-only", "a")),
                Map.of("2", domainRules(rule("artifact-only", "version"))));
        ContractRuleLoader loader = new ContractRuleLoader(new ContractRulesetCache(60), () -> client);

        List<RuleDefinition> rules = loader.domainRules("g", "a", "2");

        assertEquals(List.of("global-only", "shared", "artifact-only"),
                rules.stream().map(RuleDefinition::getName).toList());
        assertEquals(List.of("g", "artifact", "version"), rules.stream().map(RuleDefinition::getExpr).toList());
        assertEquals(List.of(0, 1, 2), rules.stream().map(RuleDefinition::getOrderIndex).toList());
    }

    @Test
    void testMissingRulesetsAreCachedToo() {
        RegistryClientFacade client = facade(null, null, Map.of());
        ContractRuleLoader loader = new ContractRuleLoader(new ContractRulesetCache(60), () -> client);

        assertEquals(List.of(), loader.domainRules("g", "a", "1"));
        assertEquals(List.of(), loader.domainRules("g", "a", "1"));

        assertEquals(List.of("getGlobalContractRuleset", "getContractRuleset", "getVersionContractRuleset"),
                calls);
    }
}
