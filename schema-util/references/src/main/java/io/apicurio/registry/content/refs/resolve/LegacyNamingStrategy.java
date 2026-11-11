package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

/**
 * Naming strategy reproducing the historical behavior of the Maven plugin's auto-references registration.
 */
public final class LegacyNamingStrategy implements ArtifactNamingStrategy {

    /**
     * How Avro reference targets are named.
     */
    public enum AvroMode {
        INHERIT_PARENT_GROUP, USE_AVRO_NAMESPACE
    }

    private final AvroMode avroMode;
    private final ArtifactTypeUtilProviderFactory factory;

    /**
     * Creates the strategy.
     *
     * @param avroMode naming mode for Avro targets
     * @param factory provider factory used to obtain reference identifier extractors
     */
    public LegacyNamingStrategy(AvroMode avroMode, ArtifactTypeUtilProviderFactory factory) {
        this.avroMode = avroMode;
        this.factory = factory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtifactCoordinates name(NamingContext context) {
        ReferenceArtifactIdentifierExtractor extractor = factory
                .getArtifactTypeProvider(context.parentArtifactType())
                .getReferenceArtifactIdentifierExtractor();
        String parentGroup = context.parent().groupId();
        String groupId = parentGroup;
        String artifactId = extractor.extractArtifactId(context.resource());
        if (ArtifactType.AVRO.equals(context.target().artifactType())) {
            if (avroMode == AvroMode.USE_AVRO_NAMESPACE) {
                String extracted = extractor.extractGroupId(context.resource());
                groupId = extracted == null ? parentGroup : extracted;
            } else {
                artifactId = context.target().avroFullName();
            }
        }
        return new ArtifactCoordinates(groupId, artifactId);
    }
}
