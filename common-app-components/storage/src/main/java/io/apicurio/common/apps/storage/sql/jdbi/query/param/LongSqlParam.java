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

package io.apicurio.common.apps.storage.sql.jdbi.query.param;

import io.apicurio.common.apps.storage.sql.jdbi.RuntimeSqlException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @author eric.wittmann@gmail.com
 */
public class LongSqlParam extends SqlParam<Long> {

    public LongSqlParam(int position, Long value) {
        super(position, value);
    }

    public void bindTo(PreparedStatement statement) {
        try {
            if (value == null) {
                statement.setNull(position + 1, Types.INTEGER);
            } else {
                statement.setLong(position + 1, value);
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }
}
