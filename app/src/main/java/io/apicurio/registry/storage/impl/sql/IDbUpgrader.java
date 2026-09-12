package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.impl.sql.jdb.Handle;

public interface IDbUpgrader {

    /**
     * Called by the {@link AbstractSqlRegistryStorage} class when upgrading the database.
     *
     * @param dbHandle
     * @param maxReferenceDepth maximum recursion depth for resolving references
     */
    public void upgrade(Handle dbHandle, int maxReferenceDepth) throws Exception;

}
