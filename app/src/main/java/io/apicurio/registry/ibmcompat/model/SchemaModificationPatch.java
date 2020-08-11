/*
 * Copyright 2020 IBM
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
package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;

import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "path")
@JsonSubTypes({
    @JsonSubTypes.Type(name = "/enabled", value = EnabledModification.class),
    @JsonSubTypes.Type(name = "/state", value = StateModification.class)
})
public abstract class SchemaModificationPatch {

    /**
     * Gets or Sets op
     */
    public enum OpEnum {
        REPLACE("replace");
        private String value;

        OpEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return value;
        }
    }

    private OpEnum op;

    /**
     * Gets or Sets path
     */
    public enum PathEnum {
        _ENABLED("/enabled"),
        _STATE("/state");
        private String value;

        PathEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return value;
        }
    }

    private PathEnum path;

    /**
     *
     **/

    @JsonProperty("op")
    @NotNull
    public OpEnum getOp() {
        return op;
    }

    public void setOp(OpEnum op) {
        this.op = op;
    }

    /**
     *
     **/

    @JsonProperty("path")
    @NotNull
    public PathEnum getPath() {
        return path;
    }

    public void setPath(PathEnum path) {
        this.path = path;
    }

    @JsonProperty("value")
    @NotNull
    public abstract Object getValue();

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaModificationPatch schemaModificationPatch = (SchemaModificationPatch) o;
        return Objects.equals(op, schemaModificationPatch.op) &&
            Objects.equals(path, schemaModificationPatch.path) &&
            Objects.equals(getValue(), schemaModificationPatch.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, path, getValue());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaModificationPatch {\n");

        sb.append("    op: ").append(op.toString()).append("\n");
        sb.append("    path: ").append(path.toString()).append("\n");
        sb.append("    value: ").append(toIndentedString(getValue())).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}
