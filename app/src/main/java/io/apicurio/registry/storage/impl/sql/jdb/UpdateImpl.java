package io.apicurio.registry.storage.impl.sql.jdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UpdateImpl extends SqlImpl<Update> implements Update {

    /**
     * Constructor.
     * 
     * @param connection
     * @param sql
     */
    public UpdateImpl(Connection connection, String sql) {
        super(connection, sql);
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Update#execute()
     */
    @Override
    public int execute() {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParametersTo(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Update#executeNoUpdate()
     */
    @Override
    public void executeNoUpdate() {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParametersTo(statement);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

}
