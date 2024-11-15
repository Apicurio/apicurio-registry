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

import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.sql.jdbi.mappers.MapperLoaderHolder;
import io.apicurio.common.apps.storage.sql.jdbi.mappers.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author eric.wittmann@gmail.com
 */
public class QueryImpl extends SqlImpl<Query> implements Query {

    private int fetchSize = -1;

    /**
     * Constructor.
     *
     * @param connection a DB connection
     * @param sql some SQL statement(s)
     */
    public QueryImpl(Connection connection, String sql) {
        super(connection, sql);
    }

    public QueryImpl(String sql) {
        super(sql);
    }

    /**
     * @see Query#setFetchSize(int)
     */
    @Override
    public Query setFetchSize(int size) {
        this.fetchSize = size;
        return this;
    }

    /**
     * @see Query#map(io.apicurio.common.apps.storage.sql.jdbi.mappers.RowMapper)
     */
    @Override
    public <T> MappedQuery<T> map(RowMapper<T> mapper) throws StorageException {
        try {
            PreparedStatement statement = this.connection.orElseThrow(() -> new StorageException(
                    "Handle not set. Use setHandleOnce(...) to set the Handle before executing the query.",
                    null)).prepareStatement(sql);
            this.bindParametersTo(statement);
            if (this.fetchSize != -1) {
                statement.setFetchSize(fetchSize);
            }
            return new MappedQueryImpl<>(statement, mapper, context);
        } catch (SQLException e) {
            throw new StorageException(context, e);
        }
    }

    /**
     * @see Query#mapTo(java.lang.Class)
     */
    @Override
    public <T> MappedQuery<T> mapTo(Class<T> klass) throws StorageException {
        RowMapper<T> mapper = this.loadMapper(klass);
        return this.map(mapper);
    }

    @SuppressWarnings("unchecked")
    private <T> RowMapper<T> loadMapper(Class<T> klass) throws StorageException {
        return (RowMapper<T>) MapperLoaderHolder.getInstance().getMapperLoader().flatMap(
                loader -> loader.getMappers().stream().filter(mapper -> mapper.supports(klass)).findFirst())
                .orElseThrow(() -> new StorageException("Row mapper not implemented for class: " + klass,
                        context));
    }
}
