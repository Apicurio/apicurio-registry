package io.apicurio.registry.storage.impl.sql.jdb;


public interface Update extends Sql<Update> {

    public int execute();

    public void executeNoUpdate();

}
