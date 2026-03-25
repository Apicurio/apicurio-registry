package io.apicurio.registry.storage.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContractRuleDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testContractRuleDtoSerialization() throws Exception {
        ContractRuleDto dto = ContractRuleDto.builder()
                .name("test-rule")
                .kind(RuleKind.CONDITION)
                .type("CEL")
                .mode(RuleMode.WRITE)
                .expr("message.field == 'value'")
                .params(Map.of("foo", "bar"))
                .tags(Set.of("tag1", "tag2"))
                .onSuccess(RuleAction.NONE)
                .onFailure(RuleAction.ERROR)
                .disabled(false)
                .orderIndex(1)
                .build();

        String json = mapper.writeValueAsString(dto);
        ContractRuleDto deserialized = mapper.readValue(json, ContractRuleDto.class);

        Assertions.assertEquals(dto, deserialized);
        Assertions.assertEquals("test-rule", deserialized.getName());
        Assertions.assertEquals(RuleKind.CONDITION, deserialized.getKind());
        Assertions.assertEquals(RuleMode.WRITE, deserialized.getMode());
        Assertions.assertEquals(RuleAction.ERROR, deserialized.getOnFailure());
        Assertions.assertEquals(1, deserialized.getOrderIndex());
    }

    @Test
    public void testContractRuleSetDtoSerialization() throws Exception {
        ContractRuleDto rule1 = ContractRuleDto.builder()
                .name("rule1")
                .kind(RuleKind.CONDITION)
                .build();
        ContractRuleDto rule2 = ContractRuleDto.builder()
                .name("rule2")
                .kind(RuleKind.TRANSFORM)
                .build();

        ContractRuleSetDto setDto = ContractRuleSetDto.builder()
                .domainRules(List.of(rule1))
                .migrationRules(List.of(rule2))
                .build();

        String json = mapper.writeValueAsString(setDto);
        ContractRuleSetDto deserialized = mapper.readValue(json, ContractRuleSetDto.class);

        Assertions.assertEquals(setDto, deserialized);
        Assertions.assertEquals(1, deserialized.getDomainRules().size());
        Assertions.assertEquals(1, deserialized.getMigrationRules().size());
        Assertions.assertEquals("rule1", deserialized.getDomainRules().get(0).getName());
        Assertions.assertEquals("rule2", deserialized.getMigrationRules().get(0).getName());
    }

    @Test
    public void testEditableContractRuleDtoSerialization() throws Exception {
        EditableContractRuleDto dto = EditableContractRuleDto.builder()
                .name("editable-rule")
                .kind(RuleKind.TRANSFORM)
                .type("JSONATA")
                .mode(RuleMode.WRITEREAD)
                .expr("$upcase(field)")
                .disabled(true)
                .build();

        String json = mapper.writeValueAsString(dto);
        EditableContractRuleDto deserialized = mapper.readValue(json, EditableContractRuleDto.class);

        Assertions.assertEquals(dto, deserialized);
        Assertions.assertEquals("editable-rule", deserialized.getName());
        Assertions.assertTrue(deserialized.isDisabled());
    }
}
