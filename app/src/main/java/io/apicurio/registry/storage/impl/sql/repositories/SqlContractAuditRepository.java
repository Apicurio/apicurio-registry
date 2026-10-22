package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.ContractAuditEntryDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.slf4j.Logger;

import java.sql.Timestamp;
import java.util.List;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

public class SqlContractAuditRepository {

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlContractAuditRepository(HandleFactory handles, SqlStatements sqlStatements,
            Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    public void insertAuditEntry(ContractAuditEntryDto entry) throws RegistryStorageException {
        log.debug("Inserting contract audit entry: {} {} {}", entry.getGroupId(),
                entry.getArtifactId(), entry.getAction());
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.insertContractAuditEntry())
                    .bind(0, normalizeGroupId(entry.getGroupId()))
                    .bind(1, entry.getArtifactId())
                    .bind(2, entry.getVersion())
                    .bind(3, entry.getAction())
                    .bind(4, entry.getPrincipal())
                    .bind(5, entry.getDetails())
                    .bind(6, new Timestamp(
                            entry.getCreatedOn() != null
                                    ? entry.getCreatedOn().getTime()
                                    : System.currentTimeMillis()))
                    .execute();
            return null;
        });
    }

    public List<ContractAuditEntryDto> getAuditLog(String groupId, String artifactId,
            int offset, int limit) throws RegistryStorageException {
        log.debug("Getting contract audit log for: {} {} (offset={}, limit={})",
                groupId, artifactId, offset, limit);
        return handles.withHandle(handle -> handle
                .createQuery(sqlStatements.selectContractAuditLog())
                .bind(0, normalizeGroupId(groupId))
                .bind(1, artifactId)
                .bind(2, limit)
                .bind(3, offset)
                .map(rs -> ContractAuditEntryDto.builder()
                        .auditId(rs.getLong("auditId"))
                        .groupId(rs.getString("groupId"))
                        .artifactId(rs.getString("artifactId"))
                        .version(rs.getString("version"))
                        .action(rs.getString("action"))
                        .principal(rs.getString("principal"))
                        .details(rs.getString("details"))
                        .createdOn(rs.getTimestamp("createdOn"))
                        .build())
                .list());
    }

    public void deleteAll() throws RegistryStorageException {
        log.debug("Deleting all contract audit entries");
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteAllContractAuditEntries()).execute();
            return null;
        });
    }
}
