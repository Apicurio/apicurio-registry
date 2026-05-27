package io.apicurio.registry.contracts.audit;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractAuditEntryDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Date;
import java.util.List;

@ApplicationScoped
public class ContractAuditService {

    @Inject
    @Current
    RegistryStorage storage;

    public void recordAction(String groupId, String artifactId, String version,
            String action, String principal, String details) {
        ContractAuditEntryDto entry = ContractAuditEntryDto.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .action(action)
                .principal(principal)
                .details(details)
                .createdOn(new Date())
                .build();
        storage.insertContractAuditEntry(entry);
    }

    public List<ContractAuditEntryDto> getAuditLog(String groupId, String artifactId,
            int offset, int limit) {
        return storage.getContractAuditLog(groupId, artifactId, offset, limit);
    }
}
