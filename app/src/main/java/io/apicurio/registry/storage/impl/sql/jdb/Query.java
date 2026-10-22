package io.apicurio.registry.storage.impl.sql.jdb;

public interface Query extends Sql<Query> {

    public Query setFetchSize(int size);

    public <T> MappedQuery<T> map(RowMapper<T> mapper);

    public <T> MappedQuery<T> mapTo(Class<T> someClass);

}
