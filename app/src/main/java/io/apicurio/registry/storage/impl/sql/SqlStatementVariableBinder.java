package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.impl.sql.jdb.Query;

@FunctionalInterface
public interface SqlStatementVariableBinder {

    void bind(Query query, int idx);

}
