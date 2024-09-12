package io.apicurio.registry.model;

import io.apicurio.registry.exception.UnreachableCodeException;
import jakarta.validation.ValidationException;

import java.util.function.BiFunction;

public class VersionExpressionParser {

    private VersionExpressionParser() {
    }

    public static GAV parse(GA ga, String versionExpression, BiFunction<GA, BranchId, GAV> branchToVersion) {
        if (VersionId.isValid(versionExpression)) {
            return new GAV(ga, versionExpression);
        }
        var parts = versionExpression.split("=");
        if (parts.length == 2) {
            if ("branch".equals(parts[0])) {
                return branchToVersion.apply(ga, new BranchId(parts[1]));
            } else {
                fail(versionExpression);
            }
        } else {
            fail(versionExpression);
        }
        throw new UnreachableCodeException();
    }

    private static void fail(String versionExpression) {
        throw new ValidationException("Could not parse version expression '" + versionExpression + "'.");
    }
}
