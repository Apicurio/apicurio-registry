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
        // A JDBC statement keeps the previous row's values, so a later row that skipped a position would
        // silently reuse one. Every row must bind positions 0 to n-1 once each, with the first row's n. The
        // first row itself is not checked against the SQL's placeholder count: a short first row has no
        // stale value to reuse, and the pinned H2, PostgreSQL, MySQL and SQL Server drivers all reject an
        // unset parameter.
        String error = shapeError(parameters, batch.isEmpty() ? parameters.size() : batch.get(0).size());
        if (error != null) {
            int row = batch.size() + 1;
            // Discard the whole batch, so a later batch on this statement starts clean.
            batch.clear();
            parameters.clear();
            throw new IllegalStateException("Batch row " + row + " " + error
                    + (row > 1 ? "; the rows added before it were discarded too." : "."));
        }
        // Transfer the list instead of copying it; the next bind starts a fresh one. Nothing can bind the
        // transferred list again, because executeBatch fails fast while parameters is not empty.
        batch.add(parameters);
        parameters = new ArrayList<>();
        return this;
    }

    /** Why a row does not bind positions 0 to width-1 exactly once each, or null when it does. */
    private static String shapeError(List<SqlParam> row, int width) {
        if (row.size() != width) {
            return "binds " + row.size() + " parameters where the first row binds " + width;
        }
        boolean[] bound = new boolean[width];
        for (SqlParam param : row) {
            int position = param.getPosition();
            if (position < 0 || position >= width) {
                return "binds position " + position + ", outside 0 to " + (width - 1);
            }
            if (bound[position]) {
                return "binds position " + position + " twice";
            }
            bound[position] = true;
        }
        return null;
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
