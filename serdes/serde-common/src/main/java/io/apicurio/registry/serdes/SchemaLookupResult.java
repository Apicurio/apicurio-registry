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

package io.apicurio.registry.serdes;

/**
 * @author Fabian Martinez
 */
public class SchemaLookupResult<T> {

    private byte[] rawSchema;
    private T parsedSchema;

    private long globalId;
    private String groupId;
    private String artifactId;
    private int version;

    public SchemaLookupResult() {
        super();
    }

    public SchemaLookupResult(byte[] rawSchema, T parsedSchema, long globalId, String groupId,
            String artifactId, int version) {
        super();
        this.rawSchema = rawSchema;
        this.parsedSchema = parsedSchema;
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
     * @param rawSchema the rawSchema to set
     */
    public void setRawSchema(byte[] rawSchema) {
        this.rawSchema = rawSchema;
    }

    /**
     * @return the parsedSchema
     */
    public T getParsedSchema() {
        return parsedSchema;
    }

    /**
     * @param parsedSchema the parsedSchema to set
     */
    public void setParsedSchema(T parsedSchema) {
        this.parsedSchema = parsedSchema;
    }

    /**
     * @return the globalId
     */
    public long getGlobalId() {
        return globalId;
    }

    /**
     * @param globalId the globalId to set
     */
    public void setGlobalId(long globalId) {
        this.globalId = globalId;
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the groupId to set
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @param artifactId the artifactId to set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return the version
     */
    public int getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(int version) {
        this.version = version;
    }

    public static <T> SchemaLookupResultBuilder<T> builder() {
        return new SchemaLookupResultBuilder<T>();
    }

    public static class SchemaLookupResultBuilder<T> {

        private byte[] rawSchema;
        private T parsedSchema;
        private long globalId;
        private String groupId;
        private String artifactId;
        private int version;

        SchemaLookupResultBuilder() {
        }

        public SchemaLookupResultBuilder<T> rawSchema(byte[] rawSchema) {
            this.rawSchema = rawSchema;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResultBuilder<T> parsedSchema(T parsedSchema) {
            this.parsedSchema = parsedSchema;
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

        public SchemaLookupResultBuilder<T> version(int version) {
            this.version = version;
            return SchemaLookupResultBuilder.this;
        }

        public SchemaLookupResult<T> build() {
            return new SchemaLookupResult<>(this.rawSchema, this.parsedSchema, this.globalId, this.groupId,
                    this.artifactId, this.version);
        }

        @Override
        public String toString() {
            return "SchemaLookupResult.SchemaLookupResultBuilder(rawSchema=" + this.rawSchema
                    + ", parsedSchema=" + this.parsedSchema + ", globalId=" + this.globalId + ", groupId="
                    + this.groupId + ", artifactId=" + this.artifactId + ", version=" + this.version + ")";
        }
    }

}
