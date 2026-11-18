package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.PostgreSQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * PostgreSQL variant of {@link NativeSequencesUpgrader}.
 */
@RegisterForReflection
public class PostgreSQLNativeSequencesUpgrader extends NativeSequencesUpgrader {

    /**
     * @see NativeSequencesUpgrader#sqlStatements()
     */
    @Override
    protected SqlStatements sqlStatements() {
        return new PostgreSQLSqlStatements();
    }
}
