package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.BigqueryGsonBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BigqueryCompatibilityChecker extends BigqueryGsonBuilder implements CompatibilityChecker {
    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact) {
        // existingArtifacts is implemented as a LazyContentList, which does not support the spliterator interface.
        // This means we cannot call stream() on it in java version 11 (does work in version 17)
        final List<String> existingArtifactsContent = new ArrayList<>();
        existingArtifacts.forEach(ch -> existingArtifactsContent.add(ch.content()));
        return testCompatibility(compatibilityLevel, existingArtifactsContent,
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
            case FORWARD: {
                toSchema(existingArtifacts.get(0)).checkCompatibilityWith(proposedSchema, differences);
                break;
            }
            case BACKWARD: {
                proposedSchema.checkCompatibilityWith(toSchema(existingArtifacts.get(0)), differences);
                break;
            }
            case FULL: {
                toSchema(existingArtifacts.get(0)).checkCompatibilityWith(proposedSchema, differences);
                proposedSchema.checkCompatibilityWith(toSchema(existingArtifacts.get(0)), differences);
                break;
            }
            case FORWARD_TRANSITIVE: {
                existingArtifacts.forEach(content -> toSchema(content).checkCompatibilityWith(proposedSchema, differences));
                break;
            }
            case BACKWARD_TRANSITIVE: {
                existingArtifacts.forEach(content -> proposedSchema.checkCompatibilityWith(toSchema(content), differences));
                break;
            }
            case FULL_TRANSITIVE: {
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
