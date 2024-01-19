package io.apicurio.registry.model;


import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VersionExpressionParserTest {


    @Test
    void testVersionExpressionParser() {
        var ga1 = new GA(null, "artifact1");

        assertEquals(new GAV(ga1, new VersionId("version1")), VersionExpressionParser.parse(ga1, "branch=latest", this::getArtifactBranchTip));
        assertEquals(new GAV(ga1, new VersionId("version2")), VersionExpressionParser.parse(ga1, "branch=1.0.x", this::getArtifactBranchTip));

        assertEquals(new GAV(ga1, new VersionId("version3")), VersionExpressionParser.parse(ga1, "version3", this::getArtifactBranchTip));

        assertThrows(ValidationException.class, () -> VersionExpressionParser.parse(ga1, "branch =1.0.x", this::getArtifactBranchTip));
        assertThrows(ValidationException.class, () -> VersionExpressionParser.parse(ga1, "branch 1.0.x", this::getArtifactBranchTip));
        assertThrows(ValidationException.class, () -> VersionExpressionParser.parse(ga1, "ranch=1.0.x", this::getArtifactBranchTip));
        assertThrows(ValidationException.class, () -> VersionExpressionParser.parse(ga1, "branch=1.0.@", this::getArtifactBranchTip));
        assertThrows(ValidationException.class, () -> VersionExpressionParser.parse(ga1, "branch=", this::getArtifactBranchTip));
    }


    private GAV getArtifactBranchTip(GA ga, BranchId branchId) {
        if (BranchId.LATEST.equals(branchId)) {
            return new GAV(ga, new VersionId("version1"));
        }
        if (new BranchId("1.0.x").equals(branchId)) {
            return new GAV(ga, new VersionId("version2"));
        }
        return null;
    }
}
