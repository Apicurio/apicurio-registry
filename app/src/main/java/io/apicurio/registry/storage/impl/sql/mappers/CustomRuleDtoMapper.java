/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.CustomRuleDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CustomRuleType;

/**
 * @author Fabian Martinez
 */
public class CustomRuleDtoMapper implements RowMapper<CustomRuleDto> {

    public static final CustomRuleDtoMapper instance = new CustomRuleDtoMapper();

    /**
     * Constructor.
     */
    private CustomRuleDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public CustomRuleDto map(ResultSet rs) throws SQLException {
        CustomRuleDto dto = new CustomRuleDto();
        dto.setId(rs.getString("ruleId"));
        String sat = rs.getString("supportedArtifactType");
        dto.setSupportedArtifactType(sat == null ? null : ArtifactType.fromValue(sat));
        dto.setDescription(rs.getString("description"));
        dto.setCustomRuleType(CustomRuleType.fromValue(rs.getString("customRuleType")));
        dto.setConfig(rs.getString("configuration"));
        return dto;
    }

}
