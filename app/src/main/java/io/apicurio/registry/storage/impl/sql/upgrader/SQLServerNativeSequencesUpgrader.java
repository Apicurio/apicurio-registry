package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.SQLServerSqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * SQL Server variant of {@link NativeSequencesUpgrader}.
 */
@RegisterForReflection
public class SQLServerNativeSequencesUpgrader extends NativeSequencesUpgrader {

    /**
     * @see NativeSequencesUpgrader#sqlStatements()
     */
    @Override
    protected SqlStatements sqlStatements() {
        return new SQLServerSqlStatements();
    }
}
