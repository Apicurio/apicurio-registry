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

import io.apicurio.common.apps.storage.exceptions.NotFoundException;
import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.exceptions.WrappedStorageException;
import io.apicurio.common.apps.storage.sql.jdbi.mappers.RowMapper;

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author eric.wittmann@gmail.com
 */
public class MappedQueryImpl<T> implements MappedQuery<T>, Closeable {

    final PreparedStatement statement;
    final RowMapper<T> mapper;
    final ResultSet resultSet;
    private final Map<String, String> context;

    /**
     * Constructor.
     *
     * @param statement a SQL prepared statement
     * @param mapper a row mapper
     * @throws SQLException if a SQL error is detected
     */
    public MappedQueryImpl(PreparedStatement statement, RowMapper<T> mapper, Map<String, String> context)
            throws SQLException {
        this.statement = statement;
        this.mapper = mapper;
        this.resultSet = statement.executeQuery();
        Objects.requireNonNull(context);
        this.context = context;
    }

    /**
     * @see MappedQuery#one()
     */
    @Override
    public T one() throws StorageException {
        T rval = null;
        try {
            if (this.resultSet.next()) {
                rval = this.mapper.map(resultSet);
                if (this.resultSet.next()) {
                    throw new StorageException("SQL error: Expected only one result but got multiple.",
                            context);
                }
            } else {
                throw new NotFoundException("SQL error: Expected only one result row but got none.", context);
            }
        } catch (SQLException e) {
            throw new StorageException(context, e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see MappedQuery#first()
     */
    @Override
    public T first() throws StorageException {
        T rval = null;
        try {
            if (this.resultSet.next()) {
                rval = this.mapper.map(resultSet);
            } else {
                throw new NotFoundException("SQL error: Expected AT LEAST one result row but got none.",
                        context);
            }
        } catch (SQLException e) {
            throw new StorageException(context, e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see MappedQuery#findOne()
     */
    @Override
    public Optional<T> findOne() throws StorageException {
        Optional<T> rval;
        try {
            if (this.resultSet.next()) {
                rval = Optional.of(this.mapper.map(resultSet));
                if (this.resultSet.next()) {
                    throw new StorageException("SQL error: Expected only one result but got multiple.",
                            context);
                }
            } else {
                rval = Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException(context, e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see MappedQuery#findFirst()
     */
    @Override
    public Optional<T> findFirst() throws StorageException {
        Optional<T> rval = null;
        try {
            if (this.resultSet.next()) {
                rval = Optional.of(this.mapper.map(resultSet));
            } else {
                rval = Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException(context, e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see MappedQuery#list()
     */
    @Override
    public List<T> list() throws StorageException {
        List<T> rval = new LinkedList<>();
        try {
            while (this.resultSet.next()) {
                T t = this.mapper.map(resultSet);
                rval.add(t);
            }
        } catch (SQLException e) {
            throw new StorageException(context, e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see MappedQuery#stream()
     */
    @Override
    public Stream<T> stream() {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE,
                Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                try {
                    if (!resultSet.next()) {
                        return false;
                    }
                    T t = mapper.map(resultSet);
                    action.accept(t);
                    return true;
                } catch (SQLException e) {
                    throw new WrappedStorageException(new StorageException(context, e));
                }
            }

        }, false).onClose(this::close);
    }

    /**
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() {
        try {
            this.statement.close();
        } catch (SQLException e) {
            throw new WrappedStorageException(new StorageException(context, e));
        }
    }
}
