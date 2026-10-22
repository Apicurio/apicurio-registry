package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.rest.v3.beans.ContractRule;
import io.apicurio.registry.rest.v3.beans.ContractRuleSet;
import io.apicurio.registry.rest.v3.beans.Params;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.dto.RuleAction;
import io.apicurio.registry.storage.dto.RuleKind;
import io.apicurio.registry.storage.dto.RuleMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public final class ContractRuleSetMapper {

    public static ContractRuleSet toBean(ContractRuleSetDto dto) {
        ContractRuleSet bean = new ContractRuleSet();
        bean.setDomainRules(dto.getDomainRules() != null
                ? dto.getDomainRules().stream().map(ContractRuleSetMapper::toRuleBean)
                        .collect(Collectors.toList())
                : Collections.emptyList());
        bean.setMigrationRules(dto.getMigrationRules() != null
                ? dto.getMigrationRules().stream().map(ContractRuleSetMapper::toRuleBean)
                        .collect(Collectors.toList())
                : Collections.emptyList());
        return bean;
    }

    public static ContractRuleSetDto toDto(ContractRuleSet bean) {
        return ContractRuleSetDto.builder()
                .domainRules(bean.getDomainRules() != null
                        ? bean.getDomainRules().stream().map(ContractRuleSetMapper::toRuleDto)
                                .collect(Collectors.toList())
                        : Collections.emptyList())
                .migrationRules(bean.getMigrationRules() != null
                        ? bean.getMigrationRules().stream().map(ContractRuleSetMapper::toRuleDto)
                                .collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    private static ContractRule toRuleBean(ContractRuleDto dto) {
        ContractRule bean = new ContractRule();
        bean.setName(dto.getName());
        if (dto.getKind() != null) {
            bean.setKind(ContractRule.Kind.fromValue(dto.getKind().name()));
        }
        bean.setType(dto.getType());
        if (dto.getMode() != null) {
            bean.setMode(ContractRule.Mode.fromValue(dto.getMode().name()));
        }
        bean.setExpr(dto.getExpr());
        if (dto.getParams() != null) {
            Params params = new Params();
            dto.getParams().forEach(params::setAdditionalProperty);
            bean.setParams(params);
        }
        if (dto.getTags() != null) {
            bean.setTags(new ArrayList<>(dto.getTags()));
        }
        if (dto.getOnSuccess() != null) {
            bean.setOnSuccess(ContractRule.OnSuccess.fromValue(dto.getOnSuccess().name()));
        }
        if (dto.getOnFailure() != null) {
            bean.setOnFailure(ContractRule.OnFailure.fromValue(dto.getOnFailure().name()));
        }
        bean.setDisabled(dto.isDisabled());
        return bean;
    }

    private static ContractRuleDto toRuleDto(ContractRule bean) {
        Map<String, String> params = null;
        if (bean.getParams() != null) {
            Map<String, String> p = new HashMap<>();
            Map<String, Object> additionalProps = bean.getParams().getAdditionalProperties();
            additionalProps.forEach((k, v) -> p.put(k, v != null ? v.toString() : null));
            params = p;
        }
        return ContractRuleDto.builder()
                .name(bean.getName())
                .kind(bean.getKind() != null ? RuleKind.valueOf(bean.getKind().value()) : null)
                .type(bean.getType())
                .mode(bean.getMode() != null ? RuleMode.valueOf(bean.getMode().value()) : null)
                .expr(bean.getExpr())
                .params(params)
                .tags(bean.getTags() != null ? new HashSet<>(bean.getTags()) : null)
                .onSuccess(bean.getOnSuccess() != null
                        ? RuleAction.valueOf(bean.getOnSuccess().value()) : null)
                .onFailure(bean.getOnFailure() != null
                        ? RuleAction.valueOf(bean.getOnFailure().value()) : null)
                .disabled(bean.getDisabled() != null && bean.getDisabled())
                .build();
    }

    private ContractRuleSetMapper() {
    }
}
