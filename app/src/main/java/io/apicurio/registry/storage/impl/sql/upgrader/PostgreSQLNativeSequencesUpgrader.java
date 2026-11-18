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

    /**
     * @see NativeSequencesUpgrader#currentSchemaExpression()
     */
    @Override
    protected String currentSchemaExpression() {
        return "current_schema()";
    }

    /**
     * @see NativeSequencesUpgrader#lockLegacyTableStatement()
     */
    @Override
    protected String lockLegacyTableStatement() {
        return "LOCK TABLE sequences IN ACCESS EXCLUSIVE MODE";
    }
}
