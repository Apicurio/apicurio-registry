package io.apicurio.registry.storage.impl.sql.jdb;

public interface Update extends Sql<Update> {

    public int execute();

    public void executeNoUpdate();

    /**
     * Adds the parameters bound so far as one row of a batch and clears them for the next row.
     */
    public Update addBatch();

    /**
     * Runs every row added with {@link #addBatch()} through one prepared statement.
     */
    public int[] executeBatch();

}
