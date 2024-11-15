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

package io.apicurio.common.apps.storage.sql.jdbi.query;

import io.apicurio.common.apps.content.handle.ContentHandle;
import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.sql.jdbi.Handle;
import io.apicurio.common.apps.storage.sql.jdbi.HandleImpl;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.BytesSqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.ContentHandleSqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.DateSqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.EnumSqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.InstantSqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.IntegerSqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.LongSqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.SqlParam;
import io.apicurio.common.apps.storage.sql.jdbi.query.param.StringSqlParam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author eric.wittmann@gmail.com
 */
@SuppressWarnings("unchecked")
public abstract class SqlImpl<Q> implements Sql<Q> {

    protected Optional<Connection> connection;
    protected final String sql;
    protected final List<SqlParam<?>> parameters;
    protected Map<String, String> context = new HashMap<>();

    /**
     * @param connection a database connection
     * @param sql some SQL statement(s)
     */
    protected SqlImpl(Connection connection, String sql) {
        this.connection = Optional.of(connection);
        this.sql = sql;
        this.parameters = new LinkedList<>();
    }

    protected SqlImpl(String sql) {
        this.connection = Optional.empty();
        this.sql = sql;
        this.parameters = new LinkedList<>();
    }

    @Override
    public Q setHandleOnce(Handle handle) throws StorageException {
        if (connection.isPresent()) {
            throw new StorageException("Handle was already set", null);
        }
        connection = Optional.of(((HandleImpl) handle).getConnection());
        return (Q) this;
    }

    @Override
    public Q setContext(String key, String value) {
        context.put(key, value);
        return (Q) this;
    }

    /**
     * @see Sql#bind(int, java.lang.String)
     */
    @Override
    public Q bind(int position, String value) {
        this.parameters.add(new StringSqlParam(position, value));
        return (Q) this;
    }

    /**
     * @see Sql#bind(int, java.lang.Long)
     */
    @Override
    public Q bind(int position, Long value) {
        this.parameters.add(new LongSqlParam(position, value));
        return (Q) this;
    }

    /**
     * @see Sql#bind(int, java.lang.Integer)
     */
    @Override
    public Q bind(int position, Integer value) {
        this.parameters.add(new IntegerSqlParam(position, value));
        return (Q) this;
    }

    /**
     * @see Sql#bind(int, java.lang.Enum)
     */
    @Override
    public Q bind(int position, Enum<?> value) {
        this.parameters.add(new EnumSqlParam(position, value));
        return (Q) this;
    }

    /**
     * @see Sql#bind(int, java.util.Date)
     */
    @Override
    public Q bind(int position, Date value) {
        this.parameters.add(new DateSqlParam(position, value));
        return (Q) this;
    }

    @Override
    public Q bind(int position, Instant value) {
        this.parameters.add(new InstantSqlParam(position, value));
        return (Q) this;
    }

    /**
     * @see Sql#bind(int, byte[])
     */
    @Override
    public Q bind(int position, byte[] value) {
        this.parameters.add(new BytesSqlParam(position, value));
        return (Q) this;
    }

    @Override
    public Q bind(int position, ContentHandle value) {
        this.parameters.add(new ContentHandleSqlParam(position, value));
        return (Q) this;
    }

    protected void bindParametersTo(PreparedStatement statement) {
        this.parameters.forEach(param -> param.bindTo(statement));
    }
}
