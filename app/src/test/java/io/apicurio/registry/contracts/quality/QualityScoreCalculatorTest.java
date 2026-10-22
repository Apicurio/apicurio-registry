package io.apicurio.registry.contracts.quality;

import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityScoreCalculatorTest {

    private static final String CONTRACT_ID = "test";
    private static final String PREFIX = ContractLabels.PREFIX + CONTRACT_ID + ".";

    @Test
    void testEmptyArtifactScoresLow() {
        var calc = create(Map.of(), null, List.of(), null);
        QualityScore score = calc.calculate("g", "a", CONTRACT_ID);
        assertTrue(score.getOverall() < 0.1f);
    }

    @Test
    void testFullScoreWithEverything() {
        var labels = Map.of(
                PREFIX + "owner.team", "team",
                PREFIX + "owner.domain", "domain",
                PREFIX + "support.contact", "a@b.com",
                PREFIX + "classification", "INTERNAL",
                PREFIX + "sla.availability", "0.999",
                PREFIX + "status", "STABLE",
                PREFIX + "id", "test");

        var ruleset = ContractRuleSetDto.builder()
                .domainRules(List.of(ContractRuleDto.builder()
                        .name("rule1").build()))
                .build();

        var calc = create(labels, ruleset, List.of("1", "2"), "A description");
        QualityScore score = calc.calculate("g", "a", CONTRACT_ID);

        assertTrue(score.getOverall() > 0.8f);
        assertTrue(score.getCompleteness() > 0.8f);
        assertEquals(1.0f, score.getCompliance(), 0.01f);
        assertEquals(1.0f, score.getStability(), 0.01f);
    }

    @Test
    void testPartialScore() {
        var labels = Map.of(PREFIX + "owner.team", "team");

        var calc = create(labels, null, List.of("1"), "A description");
        QualityScore score = calc.calculate("g", "a", CONTRACT_ID);

        assertTrue(score.getOverall() > 0.0f);
        assertTrue(score.getOverall() < 0.5f);
    }

    private QualityScoreCalculator create(Map<String, String> labels,
            ContractRuleSetDto ruleset, List<String> versions,
            String description) {
        QualityScoreCalculator calc = new QualityScoreCalculator();

        ArtifactMetaDataDto meta = new ArtifactMetaDataDto();
        meta.setLabels(labels);
        meta.setDescription(description);

        InvocationHandler handler = (proxy, method, args) -> {
            return switch (method.getName()) {
            case "getArtifactMetaData" -> meta;
            case "getArtifactContractRuleset" -> ruleset;
            case "getArtifactVersions" -> versions;
            default -> throw new UnsupportedOperationException(method.getName());
            };
        };

        Object storageProxy = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {
                        io.apicurio.registry.storage.RegistryStorage.class },
                handler);

        try {
            var field = QualityScoreCalculator.class.getDeclaredField("storage");
            field.setAccessible(true);
            field.set(calc, storageProxy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return calc;
    }
}
