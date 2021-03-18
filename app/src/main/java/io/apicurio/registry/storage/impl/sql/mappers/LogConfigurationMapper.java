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

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.types.LogLevel;

/**
 * @author Fabian Martinez
 */
public class LogConfigurationMapper implements RowMapper<LogConfigurationDto> {

    public static final LogConfigurationMapper instance = new LogConfigurationMapper();

    /**
     * Constructor.
     */
    private LogConfigurationMapper() {
    }

    @Override
    public LogConfigurationDto map(ResultSet rs, StatementContext ctx) throws SQLException {
        LogConfigurationDto dto = new LogConfigurationDto();
        dto.setLogger(rs.getString("logger"));
        dto.setLogLevel(LogLevel.fromValue(rs.getString("loglevel")));
        return dto;
    }

}
