package io.apicurio.registry.storage.error;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import lombok.Getter;


public class ArtifactBranchNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -5382272137668348037L;

    @Getter
    private final GA ga;

    @Getter
    private final BranchId branchId;


    public ArtifactBranchNotFoundException(GA ga, BranchId branchId) {
        super(message(ga, branchId));
        this.ga = ga;
        this.branchId = branchId;
    }


    private static String message(GA ga, BranchId branchId) {
        return "No branch '" + branchId + "' in artifact '" + ga + "' was found.";
    }
}
