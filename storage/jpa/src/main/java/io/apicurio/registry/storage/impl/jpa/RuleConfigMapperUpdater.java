/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.jpa;

import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.jpa.entity.Rule;
import io.apicurio.registry.storage.impl.jpa.entity.RuleConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;

public class RuleConfigMapperUpdater {

    private final List<RuleConfig> existing;

    private final Map<String, String> added = new HashMap<>();

    public RuleConfigMapperUpdater(List<RuleConfig> existing) {
        this.existing = existing;
    }

    public RuleConfigMapperUpdater() {
        this.existing = Collections.emptyList();
    }

    public RuleConfigMapperUpdater update(String key, String value) {
        added.put(key, value);
        return this;
    }

    public RuleConfigMapperUpdater update(Map<String, String> map) {
        added.putAll(map);
        return this;
    }

    public RuleConfigMapperUpdater update(RuleConfigurationDto dto) {
        this.update(this.mapToMap(dto));
        return this;
    }

    /**
     * Persist new and update existing metadata entry,
     * do not remove existing ones.
     */
    public RuleConfigMapperUpdater persistUpdate(EntityManager em, Rule rule) {
        Map<String, String> toPersist = new HashMap<>(added);

        existing.forEach(e -> {
            if (added.containsKey(e.getKey())) {
                toPersist.remove(e.getKey());
                e.setValue(added.get(e.getKey()));
                em.merge(e);
            }
        });

        toPersist.forEach((key, value) -> {
            if (value != null) {
                em.persist(
                        RuleConfig.builder()
                                .rule(rule)
                                .key(key)
                                .value(value)
                                .build()
                );
            }
        });

        return this;
    }

    private Map<String, String> merge() {
        HashMap<String, String> res = new HashMap<>();
        existing.forEach(e -> res.put(e.getKey(), e.getValue()));
        res.putAll(added);
        return res;
    }

    public RuleConfigurationDto toRuleConfigurationDto() {
        return mapToRuleConfigurationDto(merge());
    }

    public RuleConfigurationDto mapToRuleConfigurationDto(Map<String, String> map) {
        RuleConfigurationDto res = new RuleConfigurationDto();
        res.setConfiguration(map.get("config"));
        return res;
    }

    public Map<String, String> mapToMap(RuleConfigurationDto dto) {
        Map<String, String> res = new HashMap<>();
        res.put("config", dto.getConfiguration());
        return res;
    }
}
