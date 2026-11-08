package io.apicurio.registry.storage.impl.sql.jdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UpdateImpl extends SqlImpl<Update> implements Update {

    private final List<List<SqlParam>> batch = new ArrayList<>();

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

    @Override
    public Update addBatch() {
        // Transfer the list instead of copying it; the next bind starts a fresh one. Nothing can bind the
        // transferred list again, because executeBatch fails fast while parameters is not empty.
        batch.add(parameters);
        parameters = new ArrayList<>();
        return this;
    }

    @Override
    public int[] executeBatch() {
        if (!parameters.isEmpty()) {
            // Discard the whole batch, so a later batch on this statement starts clean.
            batch.clear();
            parameters.clear();
            throw new IllegalStateException("Parameters were bound after the last addBatch() and would be lost.");
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (List<SqlParam> row : batch) {
                bindParametersTo(statement, row);
                statement.addBatch();
            }
            return statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            batch.clear();
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
