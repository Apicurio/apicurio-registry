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

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

/**
 * @author eric.wittmann@gmail.com
 */
public class RuleConfigurationDtoMapper implements RowMapper<RuleConfigurationDto> {

    public static final RuleConfigurationDtoMapper instance = new RuleConfigurationDtoMapper();

    /**
     * Constructor.
     */
    private RuleConfigurationDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public RuleConfigurationDto map(ResultSet rs) throws SQLException {
        RuleConfigurationDto dto = new RuleConfigurationDto();
        dto.setConfiguration(rs.getString("configuration"));
        return dto;
    }

}