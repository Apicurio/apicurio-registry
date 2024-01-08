package io.apicurio.registry.storage.error;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GAV;
import lombok.Getter;

public class BranchVersionAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = -2869727219770505486L;

    @Getter
    private final GAV gav;

    @Getter
    private final BranchId branchId;


    public BranchVersionAlreadyExistsException(GAV gav, BranchId branchId) {
        super(message(gav, branchId));
        this.gav = gav;
        this.branchId = branchId;
    }


    private static String message(GAV gav, BranchId branchId) {
        return "Branch '" + branchId + "' already contains version '" + gav + "'.";
    }
}
