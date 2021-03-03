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
package io.apicurio.registry.mt.metadata;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * @author Fabian Martinez
 */
public class TenantMetadataDtoMapper implements RowMapper<TenantMetadataDto> {

    public static final TenantMetadataDtoMapper instance = new TenantMetadataDtoMapper();

    /**
     * Constructor.
     */
    private TenantMetadataDtoMapper() {
    }

    /**
     * @see org.jdbi.v3.core.mapper.RowMapper#map(java.sql.ResultSet, org.jdbi.v3.core.statement.StatementContext)
     */
    @Override
    public TenantMetadataDto map(ResultSet rs, StatementContext ctx) throws SQLException {
        TenantMetadataDto dto = new TenantMetadataDto();
        dto.setTenantId(rs.getString("tenantId"));
        dto.setClientId(rs.getString("authClientId"));
        dto.setAuthServerUrl(rs.getString("authServerUrl"));
        //TODO map more columns as needed
        return dto;
    }

}
