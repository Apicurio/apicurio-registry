package io.apicurio.registry.storage.impl.sql.jdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;


@SuppressWarnings("unchecked")
public abstract class SqlImpl<Q> implements Sql<Q> {

    protected final Connection connection;
    protected final String sql;
    protected final List<SqlParam> parameters;

    /**
     * @param connection
     * @param sql
     */
    public SqlImpl(Connection connection, String sql) {
        this.connection = connection;
        this.sql = sql;
        this.parameters = new LinkedList<>();
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Sql#bind(int, java.lang.String)
     */
    @Override
    public Q bind(int position, String value) {
        this.parameters.add(new SqlParam(position, value, SqlParamType.STRING));
        return (Q) this;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Sql#bind(int, java.lang.Long)
     */
    @Override
    public Q bind(int position, Long value) {
        this.parameters.add(new SqlParam(position, value, SqlParamType.LONG));
        return (Q) this;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Sql#bind(int, java.lang.Integer)
     */
    @Override
    public Q bind(int position, Integer value) {
        this.parameters.add(new SqlParam(position, value, SqlParamType.INTEGER));
        return (Q) this;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Sql#bind(int, java.lang.Enum)
     */
    @Override
    public Q bind(int position, Enum<?> value) {
        this.parameters.add(new SqlParam(position, value, SqlParamType.ENUM));
        return (Q) this;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Sql#bind(int, java.util.Date)
     */
    @Override
    public Q bind(int position, Date value) {
        this.parameters.add(new SqlParam(position, value, SqlParamType.DATE));
        return (Q) this;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Sql#bind(int, byte[])
     */
    @Override
    public Q bind(int position, byte[] value) {
        this.parameters.add(new SqlParam(position, value, SqlParamType.BYTES));
        return (Q) this;
    }

    protected void bindParametersTo(PreparedStatement statement) {
        this.parameters.forEach(param -> {
            param.bindTo(statement);
        });
    }

}
