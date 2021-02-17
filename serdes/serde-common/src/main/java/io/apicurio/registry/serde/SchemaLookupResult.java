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

/**
 * @author Fabian Martinez
 */
public class SchemaLookupResult<T> {

    private byte[] rawSchema;
    private T schema;

    private long globalId;
    private String groupId;
    private String artifactId;
    private String version;

    private SchemaLookupResult(byte[] rawSchema, T schema, long globalId, String groupId,
            String artifactId, String version) {
        super();
        this.rawSchema = rawSchema;
        this.schema = schema;
        this.globalId = globalId;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
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

    public static <T> SchemaLookupResultBuilder<T> builder() {
        return new SchemaLookupResultBuilder<T>();
    }

    public static class SchemaLookupResultBuilder<T> {

        private byte[] rawSchema;
        private T schema;
        private long globalId;
        private String groupId;
        private String artifactId;
        private String version;

        SchemaLookupResultBuilder() {
            //empty
        }

        public SchemaLookupResultBuilder<T> rawSchema(byte[] rawSchema) {
            this.rawSchema = rawSchema;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> schema(T schema) {
            this.schema = schema;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> globalId(long globalId) {
            this.globalId = globalId;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> groupId(String groupId) {
            this.groupId = groupId;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> artifactId(String artifactId) {
            this.artifactId = artifactId;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> version(String version) {
            this.version = version;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResult<T> build() {
            return new SchemaLookupResult<>(this.rawSchema, this.schema, this.globalId, this.groupId,
                    this.artifactId, this.version);
        }

    }

}
