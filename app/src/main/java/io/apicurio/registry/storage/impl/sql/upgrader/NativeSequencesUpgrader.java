package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.repositories.SqlSequenceOperations;
import io.apicurio.registry.storage.impl.sql.repositories.SqlSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Seeds the native ID sequences created by the 115 upgrade so that they continue above every ID
 * previously issued by the legacy sequences table and every ID present in the data tables.
 */
public abstract class NativeSequencesUpgrader implements IDbUpgrader {

    private static final Logger log = LoggerFactory.getLogger(NativeSequencesUpgrader.class);

    /**
     * @return statements for the dialect this upgrader runs against
     */
    protected abstract SqlStatements sqlStatements();

    /**
     * @return SQL expression evaluating to the current schema (or database) name, used to scope the
     *         information_schema lookup for the legacy sequences table
     */
    protected abstract String currentSchemaExpression();

    /**
     * Returns a statement that locks the legacy sequences table before it is read, so that an
     * in-flight allocation from a pre-115 replica cannot commit after the upgrader read the values.
     *
     * @return the lock statement, or {@code null} if the dialect locks through
     *         {@link #selectLegacyValueQuery()} instead
     */
    protected String lockLegacyTableStatement() {
        return null;
    }

    /**
     * @return query reading one legacy sequence value (bound parameter: the sequence name)
     */
    protected String selectLegacyValueQuery() {
        return "SELECT seqValue FROM sequences WHERE seqName = ?";
    }

    /**
     * @see IDbUpgrader#upgrade(Handle)
     */
    @Override
    public void upgrade(Handle handle) {
        SqlStatements statements = sqlStatements();
        SqlSequenceOperations operations = new SqlSequenceOperations(statements);
        // A re-run after a partial failure (MySQL DDL commits implicitly) may find the legacy table
        // already dropped; checked without catching errors, since a failed statement aborts the
        // whole PostgreSQL transaction.
        boolean legacyTableExists = handle.createQuery("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE LOWER(table_name) = 'sequences' AND table_schema = " + currentSchemaExpression())
                .mapTo(Long.class).one() > 0;
        if (legacyTableExists) {
            String lock = lockLegacyTableStatement();
            if (lock != null) {
                handle.createUpdate(lock).execute();
            }
        } else {
            log.info("Legacy sequences table not found; seeding native sequences from table maximums only");
        }
        Map<String, String> maxQueries = Map.of(
                SqlSequenceRepository.GLOBAL_ID_SEQUENCE, statements.selectMaxGlobalId(),
                SqlSequenceRepository.CONTENT_ID_SEQUENCE, statements.selectMaxContentId(),
                SqlSequenceRepository.COMMENT_ID_SEQUENCE, statements.selectMaxVersionCommentId());
        maxQueries.forEach((sequenceName, maxQuery) -> {
            long legacy = legacyTableExists
                    ? handle.createQuery(selectLegacyValueQuery()).bind(0, sequenceName)
                            .mapTo(Long.class).findOne().orElse(0L)
                    : 0L;
            long tableMax = handle.createQuery(maxQuery).mapTo(Long.class).findOne().orElse(0L);
            long target = Math.max(legacy, tableMax);
            operations.advanceTo(handle, sequenceName, target);
            log.info("Seeded native sequence {} to continue after {}", sequenceName, target);
        });
    }
}
