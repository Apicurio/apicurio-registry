package io.apicurio.registry.rules.app.governance;

import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GovernanceRuleExecutorTest {

    private static final String CONTRACT_ID = "test-contract";
    private static final String PREFIX = ContractLabels.PREFIX + CONTRACT_ID + ".";

    private GovernanceRuleExecutor create(Map<String, String> labels) {
        GovernanceRuleExecutor executor = new GovernanceRuleExecutor();
        ArtifactMetaDataDto dto = new ArtifactMetaDataDto();
        dto.setLabels(labels);

        InvocationHandler handler = (proxy, method, args) -> {
            if ("getArtifactMetaData".equals(method.getName())) {
                return dto;
            }
            throw new UnsupportedOperationException(method.getName());
        };
        Object storageProxy = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {
                        io.apicurio.registry.storage.RegistryStorage.class },
                handler);
        try {
            var field = GovernanceRuleExecutor.class.getDeclaredField("storage");
            field.setAccessible(true);
            field.set(executor, storageProxy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return executor;
    }

    @Test
    void testNoneLevel() {
        assertDoesNotThrow(
                () -> create(Map.of()).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.NONE));
    }

    @Test
    void testBasicPassesWithOwner() {
        var labels = Map.of(PREFIX + "owner.team", "my-team");
        assertDoesNotThrow(
                () -> create(labels).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.BASIC));
    }

    @Test
    void testBasicFailsWithoutOwner() {
        assertThrows(RuleViolationException.class,
                () -> create(Map.of()).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.BASIC));
    }

    @Test
    void testBasicBlocksDeprecated() {
        var labels = Map.of(
                PREFIX + "owner.team", "my-team",
                PREFIX + "status", "DEPRECATED");
        assertThrows(RuleViolationException.class,
                () -> create(labels).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.BASIC));
    }

    @Test
    void testFullRequiresClassification() {
        var labels = new HashMap<String, String>();
        labels.put(PREFIX + "owner.team", "my-team");
        labels.put(PREFIX + "support.contact", "a@b.com");
        assertThrows(RuleViolationException.class,
                () -> create(labels).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.FULL));
    }

    @Test
    void testFullRequiresContact() {
        var labels = new HashMap<String, String>();
        labels.put(PREFIX + "owner.team", "my-team");
        labels.put(PREFIX + "classification", "INTERNAL");
        assertThrows(RuleViolationException.class,
                () -> create(labels).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.FULL));
    }

    @Test
    void testFullPassesWithAll() {
        var labels = Map.of(
                PREFIX + "owner.team", "my-team",
                PREFIX + "classification", "INTERNAL",
                PREFIX + "support.contact", "a@b.com");
        assertDoesNotThrow(
                () -> create(labels).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.FULL));
    }

    @Test
    void testFullBlocksProdWithoutStable() {
        var labels = new HashMap<String, String>();
        labels.put(PREFIX + "owner.team", "my-team");
        labels.put(PREFIX + "classification", "INTERNAL");
        labels.put(PREFIX + "support.contact", "a@b.com");
        labels.put(PREFIX + "stage", "PROD");
        labels.put(PREFIX + "status", "DRAFT");
        assertThrows(RuleViolationException.class,
                () -> create(labels).check("g", "a", CONTRACT_ID,
                        GovernanceLevel.FULL));
    }
}
