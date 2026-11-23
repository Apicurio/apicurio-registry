package io.apicurio.registry.storage.error;

/**
 * Thrown when a data contract change was validated against a state that another request has since
 * changed, for example two concurrent promotions or status transitions.
 */
public class ContractStateConflictException extends RegistryStorageException {

    private static final long serialVersionUID = 6118923465702918311L;

    public ContractStateConflictException(String groupId, String artifactId) {
        super("The contract of artifact '" + groupId + "/" + artifactId
                + "' was changed by another request. Read it again and retry.");
    }

    public ContractStateConflictException(String message) {
        super(message);
    }
}
