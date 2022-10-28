package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.BigqueryGsonBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BigqueryCompatibilityChecker extends BigqueryGsonBuilder implements CompatibilityChecker {
    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact) {
        return testCompatibility(compatibilityLevel, existingArtifacts.stream().map(ContentHandle::content).toList(),
                proposedArtifact.content());
    }

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<String> existingArtifacts, String proposedArtifact) {
        if (compatibilityLevel.equals(CompatibilityLevel.NONE)) {
            return CompatibilityExecutionResult.compatible();
        }
        ComparableSchema proposedSchema = toSchema(proposedArtifact);
        Set<CompatibilityDifference> differences = new HashSet<>();
        switch (compatibilityLevel) {
            case FORWARD -> {
                toSchema(existingArtifacts.get(0)).checkCompatibilityWith(proposedSchema, differences);
            }
            case BACKWARD -> {
                proposedSchema.checkCompatibilityWith(toSchema(existingArtifacts.get(0)), differences);
            }
            case FULL -> {
                toSchema(existingArtifacts.get(0)).checkCompatibilityWith(proposedSchema, differences);
                proposedSchema.checkCompatibilityWith(toSchema(existingArtifacts.get(0)), differences);
            }
            case FORWARD_TRANSITIVE -> {
                existingArtifacts.forEach(content -> {
                    toSchema(content).checkCompatibilityWith(proposedSchema, differences);
                });
            }
            case BACKWARD_TRANSITIVE -> {
                existingArtifacts.forEach(content -> {
                    proposedSchema.checkCompatibilityWith(toSchema(content), differences);
                });
            }
            case FULL_TRANSITIVE -> {
                existingArtifacts.forEach(content -> {
                    toSchema(content).checkCompatibilityWith(proposedSchema, differences);
                    proposedSchema.checkCompatibilityWith(toSchema(content), differences);
                });
            }
        }
        return CompatibilityExecutionResult.incompatible(differences);
    }

    private ComparableSchema toSchema(String content) {
        return new ComparableSchema(parseFields(content));
    }
}
