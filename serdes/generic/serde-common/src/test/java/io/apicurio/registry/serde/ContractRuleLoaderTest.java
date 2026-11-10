package io.apicurio.registry.serde;

import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.resolver.client.ContractRulesetCache;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        // Each rule keeps its position within its own scope, as on the server
        assertEquals(List.of(1, 0, 0), rules.stream().map(RuleDefinition::getOrderIndex).toList());
    }

    @Test
    void testScopesInterleaveAsOnTheServer() {
        // Server: global G0, G1 and artifact A0 run as G0, A0, G1 (stable sort by order index)
        RegistryClientFacade client = facade(domainRules(rule("G0", "x"), rule("G1", "y")),
                domainRules(rule("A0", "z")), Map.of());
        ContractRuleLoader loader = new ContractRuleLoader(new ContractRulesetCache(60), () -> client);

        List<RuleDefinition> rules = new ArrayList<>(loader.domainRules("g", "a", null));
        rules.sort(Comparator.comparingInt(RuleDefinition::getOrderIndex));

        assertEquals(List.of("G0", "A0", "G1"), rules.stream().map(RuleDefinition::getName).toList());
    }

    @Test
    void testFailedFetchPropagatesAndIsNotCached() {
        ContractRuleSet artifactRules = domainRules(rule("A0", "z"));
        boolean[] fail = {true};
        RegistryClientFacade client = (RegistryClientFacade) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RegistryClientFacade.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getContractRuleset") && fail[0]) {
                        throw new IllegalStateException("registry unavailable");
                    }
                    return method.getName().equals("getContractRuleset") ? artifactRules : null;
                });
        ContractRuleLoader loader = new ContractRuleLoader(new ContractRulesetCache(60), () -> client);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> loader.domainRules("g", "a", null));
        assertEquals("registry unavailable", e.getMessage());

        fail[0] = false;
        assertEquals(List.of("A0"), loader.domainRules("g", "a", null).stream().map(RuleDefinition::getName).toList());
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
