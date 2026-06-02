package io.apicurio.registry.serde;

import io.apicurio.registry.contracts.rules.MigrationExecutor;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.contracts.rules.RuleExecutionEngine;
import io.apicurio.registry.contracts.rules.RuleExecutionResult;
import io.apicurio.registry.resolver.client.ContractRulesetCache;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleKind;
import io.apicurio.registry.rest.client.models.ContractRuleMode;
import io.apicurio.registry.rest.client.models.ContractRuleOnFailure;
import io.apicurio.registry.rest.client.models.ContractRuleSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractRuleLocalEvaluationTest {

    private RuleExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = RuleExecutionEngine.createStandalone();
    }

    // ── Domain rule evaluation ──────────────────────────────────────

    @Test
    void testDomainRulePassesValidRecord() {
        ContractRuleSet ruleset = createDomainRuleset("totalAmount > 0");
        List<RuleDefinition> rules = AbstractSerializer.toRuleDefinitions(ruleset);

        Map<String, Object> record = Map.of("totalAmount", 99.99);
        RuleExecutionResult result = engine.execute(rules, "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(1, result.getExecutedRules());
        assertEquals(0, result.getFailedRules());
    }

    @Test
    void testDomainRuleRejectsInvalidRecord() {
        ContractRuleSet ruleset = createDomainRuleset("totalAmount > 0");
        List<RuleDefinition> rules = AbstractSerializer.toRuleDefinitions(ruleset);

        Map<String, Object> record = Map.of("totalAmount", -5.0);
        RuleExecutionResult result = engine.execute(rules, "WRITE", record);

        assertFalse(result.isPassed());
        assertEquals(1, result.getExecutedRules());
        assertEquals(1, result.getFailedRules());
        assertEquals(1, result.getViolations().size());
    }

    @Test
    void testDomainRuleWithReadMode() {
        ContractRule rule = createContractRule("check-id", ContractRuleKind.CONDITION,
                "CEL", ContractRuleMode.READ, "size(orderId) > 0",
                ContractRuleOnFailure.ERROR);
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(rule));
        ruleset.setMigrationRules(List.of());

        List<RuleDefinition> rules = AbstractSerializer.toRuleDefinitions(ruleset);

        Map<String, Object> record = Map.of("orderId", "ORD-123");
        RuleExecutionResult result = engine.execute(rules, "READ", record);

        assertTrue(result.isPassed());
    }

    @Test
    void testDomainRuleWriteModeNotEvaluatedOnRead() {
        ContractRule rule = createContractRule("write-only", ContractRuleKind.CONDITION,
                "CEL", ContractRuleMode.WRITE, "totalAmount > 0",
                ContractRuleOnFailure.ERROR);
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(rule));
        ruleset.setMigrationRules(List.of());

        List<RuleDefinition> rules = AbstractSerializer.toRuleDefinitions(ruleset);

        Map<String, Object> record = Map.of("totalAmount", -5.0);
        RuleExecutionResult result = engine.execute(rules, "READ", record);

        assertTrue(result.isPassed());
        assertEquals(0, result.getExecutedRules());
    }

    @Test
    void testDisabledRuleIsSkipped() {
        ContractRule rule = createContractRule("disabled-rule", ContractRuleKind.CONDITION,
                "CEL", ContractRuleMode.WRITE, "totalAmount > 0",
                ContractRuleOnFailure.ERROR);
        rule.setDisabled(true);
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(rule));
        ruleset.setMigrationRules(List.of());

        List<RuleDefinition> rules = AbstractSerializer.toRuleDefinitions(ruleset);

        Map<String, Object> record = Map.of("totalAmount", -5.0);
        RuleExecutionResult result = engine.execute(rules, "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(0, result.getExecutedRules());
    }

    @Test
    void testMultipleDomainRulesAllPass() {
        ContractRule r1 = createContractRule("positive-amount", ContractRuleKind.CONDITION,
                "CEL", ContractRuleMode.WRITE, "totalAmount > 0",
                ContractRuleOnFailure.ERROR);
        ContractRule r2 = createContractRule("has-id", ContractRuleKind.CONDITION,
                "CEL", ContractRuleMode.WRITE, "size(orderId) > 0",
                ContractRuleOnFailure.ERROR);
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(r1, r2));
        ruleset.setMigrationRules(List.of());

        List<RuleDefinition> rules = AbstractSerializer.toRuleDefinitions(ruleset);

        Map<String, Object> record = Map.of("totalAmount", 50.0, "orderId", "ORD-1");
        RuleExecutionResult result = engine.execute(rules, "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(2, result.getExecutedRules());
    }

    @Test
    void testEmptyRulesetPasses() {
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of());
        ruleset.setMigrationRules(List.of());

        List<RuleDefinition> rules = AbstractSerializer.toRuleDefinitions(ruleset);
        assertTrue(rules.isEmpty());
    }

    // ── RuleDefinition conversion ───────────────────────────────────

    @Test
    void testToRuleDefinitionMapsAllFields() {
        ContractRule r = createContractRule("test-rule", ContractRuleKind.CONDITION,
                "CEL", ContractRuleMode.WRITE, "x > 0", ContractRuleOnFailure.ERROR);
        r.setDisabled(false);

        RuleDefinition def = AbstractSerializer.toRuleDefinition(r);

        assertEquals("test-rule", def.getName());
        assertEquals("CONDITION", def.getKind());
        assertEquals("CEL", def.getType());
        assertEquals("WRITE", def.getMode());
        assertEquals("x > 0", def.getExpr());
        assertEquals("ERROR", def.getOnFailure());
        assertFalse(def.isDisabled());
        assertTrue(def.isCondition());
        assertFalse(def.isTransform());
    }

    @Test
    void testToRuleDefinitionHandlesNulls() {
        ContractRule r = new ContractRule();
        r.setName("null-rule");
        r.setType("CEL");
        r.setExpr("true");

        RuleDefinition def = AbstractSerializer.toRuleDefinition(r);

        assertEquals("null-rule", def.getName());
        assertNull(def.getKind());
        assertNull(def.getMode());
        assertNull(def.getOnFailure());
        assertFalse(def.isDisabled());
    }

    // ── Cache ───────────────────────────────────────────────────────

    @Test
    void testCacheHitReturnsRuleset() {
        ContractRulesetCache cache = new ContractRulesetCache(60);
        ContractRuleSet ruleset = createDomainRuleset("x > 0");

        cache.put("group", "artifact", null, ruleset);
        ContractRuleSet cached = cache.get("group", "artifact", null);

        assertNotNull(cached);
        assertEquals(1, cached.getDomainRules().size());
    }

    @Test
    void testCacheMissReturnsNull() {
        ContractRulesetCache cache = new ContractRulesetCache(60);

        assertNull(cache.get("group", "artifact", null));
    }

    @Test
    void testCacheExpiresAfterTtl() throws InterruptedException {
        ContractRulesetCache cache = new ContractRulesetCache(1);
        cache.put("group", "artifact", null, createDomainRuleset("x > 0"));

        assertNotNull(cache.get("group", "artifact", null));

        Thread.sleep(1100);

        assertNull(cache.get("group", "artifact", null));
    }

    @Test
    void testCacheSeparatesArtifactAndVersionScopes() {
        ContractRulesetCache cache = new ContractRulesetCache(60);
        ContractRuleSet artifactRules = createDomainRuleset("artifact_rule");
        ContractRuleSet versionRules = createDomainRuleset("version_rule");

        cache.put("g", "a", null, artifactRules);
        cache.put("g", "a", "v1", versionRules);

        assertEquals("artifact_rule", cache.get("g", "a", null).getDomainRules().get(0).getExpr());
        assertEquals("version_rule", cache.get("g", "a", "v1").getDomainRules().get(0).getExpr());
    }

    @Test
    void testVersionListCache() {
        ContractRulesetCache cache = new ContractRulesetCache(60);

        assertNull(cache.getVersionList("g", "a"));

        cache.putVersionList("g", "a", List.of("1", "2", "3"));

        List<String> versions = cache.getVersionList("g", "a");
        assertNotNull(versions);
        assertEquals(3, versions.size());
        assertEquals("2", versions.get(1));
    }

    @Test
    void testVersionListCacheExpires() throws InterruptedException {
        ContractRulesetCache cache = new ContractRulesetCache(1);
        cache.putVersionList("g", "a", List.of("1", "2"));

        assertNotNull(cache.getVersionList("g", "a"));

        Thread.sleep(1100);

        assertNull(cache.getVersionList("g", "a"));
    }

    // ── Migration ───────────────────────────────────────────────────

    @Test
    void testMigrationSingleHop() {
        MigrationExecutor migrator = new MigrationExecutor(engine);
        List<String> versions = List.of("1", "2");

        RuleExecutionResult result = migrator.execute(versions, "1", "2",
                Map.of("orderId", "ORD-1", "totalAmount", 50.0),
                (version, mode) -> {
                    if ("2".equals(version) && "UPGRADE".equals(mode)) {
                        RuleDefinition rule = new RuleDefinition();
                        rule.setName("add-currency");
                        rule.setKind("TRANSFORM");
                        rule.setType("JSONATA");
                        rule.setMode("UPGRADE");
                        rule.setExpr("$ ~> |$|{\"currency\": \"USD\"}|");
                        return List.of(rule);
                    }
                    return List.of();
                });

        assertTrue(result.isPassed());
        assertNotNull(result.getTransformedRecord());
        assertEquals("USD", result.getTransformedRecord().get("currency"));
        assertEquals("ORD-1", result.getTransformedRecord().get("orderId"));
    }

    @Test
    void testMigrationMultiHop() {
        MigrationExecutor migrator = new MigrationExecutor(engine);
        List<String> versions = List.of("1", "2", "3");

        RuleExecutionResult result = migrator.execute(versions, "1", "3",
                Map.of("orderId", "ORD-1"),
                (version, mode) -> {
                    if (!"UPGRADE".equals(mode)) return List.of();
                    RuleDefinition rule = new RuleDefinition();
                    rule.setKind("TRANSFORM");
                    rule.setType("JSONATA");
                    rule.setMode("UPGRADE");
                    if ("2".equals(version)) {
                        rule.setName("v1-to-v2");
                        rule.setExpr("$ ~> |$|{\"step\": \"v2\"}|");
                    } else if ("3".equals(version)) {
                        rule.setName("v2-to-v3");
                        rule.setExpr("$ ~> |$|{\"step\": \"v3\"}|");
                    } else {
                        return List.of();
                    }
                    return List.of(rule);
                });

        assertTrue(result.isPassed());
        assertNotNull(result.getTransformedRecord());
        assertEquals("v3", result.getTransformedRecord().get("step"));
    }

    @Test
    void testMigrationNoRulesReturnsOriginal() {
        MigrationExecutor migrator = new MigrationExecutor(engine);

        RuleExecutionResult result = migrator.execute(
                List.of("1", "2"), "1", "2",
                Map.of("orderId", "ORD-1"),
                (version, mode) -> List.of());

        assertTrue(result.isPassed());
    }

    @Test
    void testMigrationSameVersionNoOp() {
        MigrationExecutor migrator = new MigrationExecutor(engine);

        RuleExecutionResult result = migrator.execute(
                List.of("1", "2"), "1", "1",
                Map.of("orderId", "ORD-1"),
                (version, mode) -> List.of());

        assertTrue(result.isPassed());
        assertEquals(0, result.getExecutedRules());
    }

    @Test
    void testMigrationUnknownVersionReturnsEmpty() {
        MigrationExecutor migrator = new MigrationExecutor(engine);

        RuleExecutionResult result = migrator.execute(
                List.of("1", "2"), "1", "999",
                Map.of("orderId", "ORD-1"),
                (version, mode) -> List.of());

        assertTrue(result.isPassed());
        assertEquals(0, result.getExecutedRules());
    }

    @Test
    void testMigrationDowngrade() {
        MigrationExecutor migrator = new MigrationExecutor(engine);
        List<String> versions = List.of("1", "2");

        RuleExecutionResult result = migrator.execute(versions, "2", "1",
                Map.of("orderId", "ORD-1", "currency", "USD"),
                (version, mode) -> {
                    if ("1".equals(version) && "DOWNGRADE".equals(mode)) {
                        RuleDefinition rule = new RuleDefinition();
                        rule.setName("remove-currency");
                        rule.setKind("TRANSFORM");
                        rule.setType("JSONATA");
                        rule.setMode("DOWNGRADE");
                        rule.setExpr("$ ~> |$|{}, [\"currency\"]|");
                        return List.of(rule);
                    }
                    return List.of();
                });

        assertTrue(result.isPassed());
        assertNotNull(result.getTransformedRecord());
        assertFalse(result.getTransformedRecord().containsKey("currency"));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private ContractRuleSet createDomainRuleset(String expr) {
        ContractRule rule = createContractRule("test-rule", ContractRuleKind.CONDITION,
                "CEL", ContractRuleMode.WRITE, expr, ContractRuleOnFailure.ERROR);
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(rule));
        ruleset.setMigrationRules(List.of());
        return ruleset;
    }

    private ContractRule createContractRule(String name, ContractRuleKind kind,
            String type, ContractRuleMode mode, String expr,
            ContractRuleOnFailure onFailure) {
        ContractRule rule = new ContractRule();
        rule.setName(name);
        rule.setKind(kind);
        rule.setType(type);
        rule.setMode(mode);
        rule.setExpr(expr);
        rule.setOnFailure(onFailure);
        rule.setDisabled(false);
        return rule;
    }
}
