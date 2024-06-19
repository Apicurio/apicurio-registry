package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

public class SchemaLookupResult<T> {

    private ParsedSchema<T> parsedSchema;

    private long globalId;
    private long contentId;
    private String contentHash;
    private String groupId;
    private String artifactId;
    private String version;

    private SchemaLookupResult() {
        // empty initialize manually
    }

    /**
     * @return the parsedSchema
     */
    public ParsedSchema<T> getParsedSchema() {
        return parsedSchema;
    }

    /**
     * @return the globalId
     */
    public long getGlobalId() {
        return globalId;
    }

    /**
     * @return the contentId
     */
    public long getContentId() {
        return contentId;
    }

    /**
     * @return the contentHash
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    public ArtifactReference toArtifactReference() {
        return ArtifactReference.builder().globalId(this.getGlobalId()).contentId(this.getContentId())
                .contentHash(this.getContentHash()).groupId(this.getGroupId())
                .artifactId(this.getArtifactId()).version(this.getVersion()).build();
    }

    public ArtifactCoordinates toArtifactCoordinates() {
        return ArtifactCoordinates.builder().groupId(this.getGroupId()).artifactId(this.getArtifactId())
                .version(this.getVersion()).build();
    }

    public static <T> SchemaLookupResultBuilder<T> builder() {
        return new SchemaLookupResultBuilder<T>();
    }

    public static class SchemaLookupResultBuilder<T> {

        private SchemaLookupResult<T> result;

        SchemaLookupResultBuilder() {
            this.result = new SchemaLookupResult<>();
        }

        public SchemaLookupResultBuilder<T> parsedSchema(ParsedSchema<T> parsedSchema) {
            this.result.parsedSchema = parsedSchema;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> globalId(long globalId) {
            this.result.globalId = globalId;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> contentId(long contentId) {
            this.result.contentId = contentId;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> contentHash(String contentHash) {
            this.result.contentHash = contentHash;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> groupId(String groupId) {
            this.result.groupId = groupId;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> artifactId(String artifactId) {
            this.result.artifactId = artifactId;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> version(String version) {
            this.result.version = version;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResult<T> build() {
            return this.result;
        }

    }

}
