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

package io.apicurio.registry.storage.impl.sql.jdb;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

/**
 * @author eric.wittmann@gmail.com
 */
public class SqlParam {

    private final int position;
    private final Object value;
    private final SqlParamType type;

    /**
     * Constructor.
     * @param position
     * @param value
     * @param type
     */
    public SqlParam(int position, Object value, SqlParamType type) {
        this.position = position;
        this.value = value;
        this.type = type;
    }

    /**
     * Binds this SQL parameter to the given statement.
     * @param statement
     */
    public void bindTo(PreparedStatement statement) {
        int position = this.position + 1; // convert from sensible position (starts at 0) to JDBC position index (starts at 1)
        try {
            switch (type) {
                case BYTES:
                    statement.setBytes(position, (byte[]) value);
                    break;
                case DATE:
                    if (value == null) {
                        statement.setNull(position, Types.TIMESTAMP);
                    } else {
                        Timestamp ts = new Timestamp(((Date) value).getTime());
                        statement.setTimestamp(position, ts);
                    }
                    break;
                case ENUM:
                    if (value == null) {
                        statement.setNull(position, Types.VARCHAR);
                    } else {
                        statement.setString(position, ((Enum<?>) value).name());
                    }
                    break;
                case INTEGER:
                    if (value == null) {
                        statement.setNull(position, Types.INTEGER);
                    } else {
                        statement.setInt(position, (Integer) value);
                    }
                    break;
                case LONG:
                    if (value == null) {
                        statement.setNull(position, Types.INTEGER);
                    } else {
                        statement.setLong(position, (Long) value);
                    }
                    break;
                case STRING:
                    statement.setString(position, (String) value);
                    break;
                default:
                    throw new RuntimeSqlException("bindTo not supported for SqlParamType: " + type);
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }
}
