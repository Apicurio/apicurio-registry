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

import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;


public class RoleMappingDtoMapper implements RowMapper<RoleMappingDto> {

    public static final RoleMappingDtoMapper instance = new RoleMappingDtoMapper();

    /**
     * Constructor.
     */
    private RoleMappingDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public RoleMappingDto map(ResultSet rs) throws SQLException {
        RoleMappingDto dto = new RoleMappingDto();
        dto.setPrincipalId(rs.getString("principalId"));
        dto.setRole(rs.getString("role"));
        dto.setPrincipalName(rs.getString("principalName"));
        return dto;
    }

}