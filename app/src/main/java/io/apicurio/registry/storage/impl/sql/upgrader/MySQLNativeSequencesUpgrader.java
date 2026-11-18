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

    /**
     * @see NativeSequencesUpgrader#currentSchemaExpression()
     */
    @Override
    protected String currentSchemaExpression() {
        return "DATABASE()";
    }

    /**
     * Best effort: the row locks are released by the implicit commit of the subsequent DROP TABLE.
     *
     * @see NativeSequencesUpgrader#selectLegacyValueQuery()
     */
    @Override
    protected String selectLegacyValueQuery() {
        return "SELECT seqValue FROM sequences WHERE seqName = ? FOR UPDATE";
    }
}
