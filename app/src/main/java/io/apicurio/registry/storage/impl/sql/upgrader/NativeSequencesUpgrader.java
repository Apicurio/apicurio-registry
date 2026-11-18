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
     * @see IDbUpgrader#upgrade(Handle)
     */
    @Override
    public void upgrade(Handle handle) {
        SqlStatements statements = sqlStatements();
        SqlSequenceOperations operations = new SqlSequenceOperations(statements);
        Map<String, String> maxQueries = Map.of(
                SqlSequenceRepository.GLOBAL_ID_SEQUENCE, statements.selectMaxGlobalId(),
                SqlSequenceRepository.CONTENT_ID_SEQUENCE, statements.selectMaxContentId(),
                SqlSequenceRepository.COMMENT_ID_SEQUENCE, statements.selectMaxVersionCommentId());
        maxQueries.forEach((sequenceName, maxQuery) -> {
            long legacy = handle.createQuery("SELECT seqValue FROM sequences WHERE seqName = ?")
                    .bind(0, sequenceName).mapTo(Long.class).findOne().orElse(0L);
            long tableMax = handle.createQuery(maxQuery).mapTo(Long.class).findOne().orElse(0L);
            long target = Math.max(legacy, tableMax);
            operations.advanceTo(handle, sequenceName, target);
            log.info("Seeded native sequence {} to continue after {}", sequenceName, target);
        });
    }
}
