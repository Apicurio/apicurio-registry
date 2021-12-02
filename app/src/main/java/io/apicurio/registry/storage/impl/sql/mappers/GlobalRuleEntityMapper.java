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

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;

/**
 * @author eric.wittmann@gmail.com
 */
public class GlobalRuleEntityMapper implements RowMapper<GlobalRuleEntity> {

    public static final GlobalRuleEntityMapper instance = new GlobalRuleEntityMapper();

    /**
     * Constructor.
     */
    private GlobalRuleEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public GlobalRuleEntity map(ResultSet rs) throws SQLException {
        GlobalRuleEntity entity = new GlobalRuleEntity();
        entity.ruleType = RuleType.fromValue(rs.getString("type"));
        entity.configuration = rs.getString("configuration");
        return entity;
    }

}