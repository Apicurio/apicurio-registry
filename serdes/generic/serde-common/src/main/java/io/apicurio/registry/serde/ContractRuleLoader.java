package io.apicurio.registry.serde;

import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.resolver.client.ContractRulesetCache;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Loads the contract rules a serializer or deserializer evaluates, with the same scoping as the
 * registry's rule execution endpoint: the global, artifact and version rulesets, merged by rule
 * name so that the narrowest scope wins. Rulesets are cached for the configured TTL, including
 * empty ones, so that an artifact without rules costs no request per record.
 */
final class ContractRuleLoader {

    /** Cache coordinates of the global ruleset, which no artifact can have. */
    private static final String GLOBAL = "__GLOBAL__";
    private static final ContractRuleSet EMPTY = new ContractRuleSet();

    private final ContractRulesetCache cache;
    private final Supplier<RegistryClientFacade> facade;

    ContractRuleLoader(ContractRulesetCache cache, Supplier<RegistryClientFacade> facade) {
        this.cache = cache;
        this.facade = facade;
    }

    /**
     * The domain rules that apply to a version: global, then artifact, then version rules, a rule
     * replacing any rule of the same name from a broader scope.
     */
    List<RuleDefinition> domainRules(String groupId, String artifactId, String version) {
        Map<String, ContractRule> merged = new LinkedHashMap<>();
        addDomainRules(merged, ruleset(GLOBAL, GLOBAL, null, RegistryClientFacade::getGlobalContractRuleset));
        addDomainRules(merged, ruleset(groupId, artifactId, null,
                f -> f.getContractRuleset(groupId, artifactId)));
        if (version != null) {
            addDomainRules(merged, ruleset(groupId, artifactId, version,
                    f -> f.getVersionContractRuleset(groupId, artifactId, version)));
        }
        return toRuleDefinitions(merged.values());
    }

    /**
     * The migration rules of one version, which a migration applies when it moves to that version.
     */
    List<RuleDefinition> migrationRules(String groupId, String artifactId, String version) {
        ContractRuleSet ruleset = ruleset(groupId, artifactId, version,
                f -> f.getVersionContractRuleset(groupId, artifactId, version));
        return ruleset.getMigrationRules() == null ? List.of()
                : toRuleDefinitions(ruleset.getMigrationRules());
    }

    private ContractRuleSet ruleset(String groupId, String artifactId, String version,
            Function<RegistryClientFacade, ContractRuleSet> fetch) {
        ContractRuleSet cached = cache.get(groupId, artifactId, version);
        if (cached != null) {
            return cached;
        }
        RegistryClientFacade client = facade.get();
        if (client == null) {
            return EMPTY;
        }
        ContractRuleSet fetched = fetch.apply(client);
        ContractRuleSet result = fetched != null ? fetched : EMPTY;
        cache.put(groupId, artifactId, version, result);
        return result;
    }

    private static void addDomainRules(Map<String, ContractRule> merged, ContractRuleSet ruleset) {
        if (ruleset.getDomainRules() == null) {
            return;
        }
        for (ContractRule rule : ruleset.getDomainRules()) {
            // Remove first, so that an overriding rule takes the position of its own scope
            merged.remove(rule.getName());
            merged.put(rule.getName(), rule);
        }
    }

    private static List<RuleDefinition> toRuleDefinitions(Iterable<ContractRule> rules) {
        List<RuleDefinition> result = new ArrayList<>();
        int order = 0;
        for (ContractRule rule : rules) {
            result.add(toRuleDefinition(rule, order++));
        }
        return result;
    }

    static RuleDefinition toRuleDefinition(ContractRule r, int order) {
        RuleDefinition def = new RuleDefinition();
        def.setName(r.getName());
        def.setKind(r.getKind() != null ? r.getKind().getValue() : null);
        def.setType(r.getType());
        def.setMode(r.getMode() != null ? r.getMode().getValue() : null);
        def.setExpr(r.getExpr());
        def.setOnFailure(r.getOnFailure() != null ? r.getOnFailure().getValue() : null);
        def.setDisabled(r.getDisabled() != null && r.getDisabled());
        def.setOrderIndex(order);
        return def;
    }
}
