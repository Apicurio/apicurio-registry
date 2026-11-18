package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.MySQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * MySQL variant of {@link NativeSequencesUpgrader}.
 */
@RegisterForReflection
public class MySQLNativeSequencesUpgrader extends NativeSequencesUpgrader {

    /**
     * @see NativeSequencesUpgrader#sqlStatements()
     */
    @Override
    protected SqlStatements sqlStatements() {
        return new MySQLSqlStatements();
    }
}
