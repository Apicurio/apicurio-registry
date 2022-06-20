/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import io.apicurio.registry.serde.strategy.ArtifactReference;

/**
 * This class is deprecated and eventually will be replaced by {@link io.apicurio.registry.resolver.SchemaLookupResult}
 * @author Fabian Martinez
 */
@Deprecated
public class SchemaLookupResult<T> {

    private byte[] rawSchema;
    private T schema;

    private long globalId;
    private long contentId;
    private String groupId;
    private String artifactId;
    private String version;

    private SchemaLookupResult() {
        //empty initialize manually
    }

    /**
     * @return the rawSchema
     */
    public byte[] getRawSchema() {
        return rawSchema;
    }

    /**
     * @return the schema
     */
    public T getSchema() {
        return schema;
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
        return ArtifactReference.builder()
                .globalId(this.getGlobalId())
                .contentId(this.getContentId())
                .groupId(this.getGroupId())
                .artifactId(this.getArtifactId())
                .version(this.getVersion())
                .build();
    }

    public ArtifactCoordinates toArtifactCoordinates() {
        return ArtifactCoordinates.builder()
                .groupId(this.getGroupId())
                .artifactId(this.getArtifactId())
                .version(this.getVersion())
                .build();
    }

    @SuppressWarnings("rawtypes")
    public io.apicurio.registry.resolver.SchemaLookupResult toCompat() {
        return io.apicurio.registry.resolver.SchemaLookupResult.builder()
            .contentId(contentId)
            .globalId(globalId)
            .groupId(groupId)
            .artifactId(artifactId)
            .version(version)
            .parsedSchema(new ParsedSchemaImpl<>().setParsedSchema(schema).setRawSchema(rawSchema))
            .build();
    }

    public static <T> SchemaLookupResultBuilder<T> builder() {
        return new SchemaLookupResultBuilder<T>();
    }

    public static class SchemaLookupResultBuilder<T> {

        private SchemaLookupResult<T> result;

        SchemaLookupResultBuilder() {
            this.result = new SchemaLookupResult<>();
        }

        public SchemaLookupResultBuilder<T> rawSchema(byte[] rawSchema) {
            this.result.rawSchema = rawSchema;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> schema(T schema) {
            this.result.schema = schema;
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