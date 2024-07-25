package io.apicurio.registry.storage.impl.sql.jdb;

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MappedQueryImpl<T> implements MappedQuery<T>, Closeable {

    final PreparedStatement statement;
    final RowMapper<T> mapper;

    /**
     * Constructor.
     * 
     * @param statement
     * @param mapper
     * @throws SQLException
     */
    public MappedQueryImpl(PreparedStatement statement, RowMapper<T> mapper) throws SQLException {
        this.statement = statement;
        this.mapper = mapper;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.MappedQuery#one()
     */
    @Override
    public T one() {
        T rval = null;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                rval = this.mapper.map(resultSet);
                if (resultSet.next()) {
                    throw new RuntimeSqlException("SQL error: Expected only one result but got multiple.");
                }
            } else {
                throw new RuntimeSqlException("SQL error: Expected only one result row but got none.");
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.MappedQuery#first()
     */
    @Override
    public T first() {
        T rval = null;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                rval = this.mapper.map(resultSet);
            } else {
                throw new RuntimeSqlException("SQL error: Expected AT LEAST one result row but got none.");
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.MappedQuery#findOne()
     */
    @Override
    public Optional<T> findOne() {
        Optional<T> rval;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                rval = Optional.of(this.mapper.map(resultSet));
                if (resultSet.next()) {
                    throw new RuntimeSqlException("SQL error: Expected only one result but got multiple.");
                }
            } else {
                rval = Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.MappedQuery#findFirst()
     */
    @Override
    public Optional<T> findFirst() {
        Optional<T> rval = null;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                rval = Optional.of(this.mapper.map(resultSet));
            } else {
                rval = Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.MappedQuery#findLast()
     */
    @Override
    public Optional<T> findLast() {
        Optional<T> rval = null;
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rval = Optional.of(this.mapper.map(resultSet));
            }
            if (rval == null) {
                rval = Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.MappedQuery#list()
     */
    @Override
    public List<T> list() {
        List<T> rval = new LinkedList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                T t = this.mapper.map(resultSet);
                rval.add(t);
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            close();
        }
        return rval;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.MappedQuery#stream()
     */
    @Override
    public Stream<T> stream() {
        try {
            ResultSet resultSet = statement.executeQuery();
            return StreamSupport
                    .stream(new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE, Spliterator.IMMUTABLE
                            | Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL) {
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
                                throw new RuntimeSqlException(e);
                            }
                        }

                    }, false).onClose(() -> {
                        try {
                            resultSet.close();
                            close();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() {
        try {
            this.statement.close();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

}
