package io.apicurio.registry.rules;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CompatibilityLevel;
import io.apicurio.registry.types.Current;

import java.util.Collections;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class RulesServiceImpl implements RulesService {

    @Inject
    @Current
    RegistryStorage storage;

    public void validate(RuleContext context) {
        switch (context.getRuleType()) {
            case COMPATIBILITY:
                if (!isCompatible(context.getArtifactType(), context.getLevel(), context.getArtifactId(), context.getContent())) {
                    throw new RulesException(String.format("Incompatible artifact: %s [%s]", context.getArtifactId(), context.getArtifactType()));
                }
                break;
            case SYNTAX_VALIDATION:
                break;
            case SEMANTIC_VALIDATION:
                break;
        }
    }

    public boolean isCompatible(ArtifactType type, CompatibilityLevel level, String artifactId, String content) {
        StoredArtifact artifact = storage.getArtifact(artifactId); // check against the last one
        return type.getAdapter().isCompatibleWith(level.name(), Collections.singletonList(artifact.content), content);
    }
}
