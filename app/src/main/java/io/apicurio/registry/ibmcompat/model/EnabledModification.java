package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class EnabledModification {


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
            return String.valueOf(value);
        }
    }

    private OpEnum op;

    /**
     * Gets or Sets path
     */
    public enum PathEnum {
        _ENABLED("/enabled");
        private String value;

        PathEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }

    private PathEnum path;
    private Boolean value;

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

    /**
     *
     **/

    @JsonProperty("value")
    @NotNull
    public Boolean getValue() {
        return value;
    }

    public void setValue(Boolean value) {
        this.value = value;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EnabledModification enabledModification = (EnabledModification) o;
        return Objects.equals(op, enabledModification.op) &&
               Objects.equals(path, enabledModification.path) &&
               Objects.equals(value, enabledModification.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, path, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class EnabledModification {\n");

        sb.append("    op: ").append(toIndentedString(op)).append("\n");
        sb.append("    path: ").append(toIndentedString(path)).append("\n");
        sb.append("    value: ").append(toIndentedString(value)).append("\n");
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

