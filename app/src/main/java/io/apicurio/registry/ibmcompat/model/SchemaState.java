/*
 * Copyright 2020 Red Hat
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SchemaState {


    /**
     * If the schema state is &#39;deprecated&#39;, all the schema version states are &#39;deprecated&#39;.
     */
    public enum StateEnum {
        DEPRECATED("deprecated"),

        ACTIVE("active");
        private String value;

        StateEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }

    private StateEnum state;
    private String comment;

    /**
     * If the schema state is &#39;deprecated&#39;, all the schema version states are &#39;deprecated&#39;.
     **/

    @JsonProperty("state")
    @NotNull
    public StateEnum getState() {
        return state;
    }

    public void setState(StateEnum state) {
        this.state = state;
    }

    /**
     * User-provided string to explain why a schema is deprecated. Ignored if the state is &#39;active&#39;. If the schema state is &#39;deprecated&#39;, the schema version state comment will match the schema state comment, unless a specific state comment is set for the schema version.
     **/

    @JsonProperty("comment")
    @Size(max = 300)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaState schemaState = (SchemaState) o;
        return Objects.equals(state, schemaState.state) &&
               Objects.equals(comment, schemaState.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, comment);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaState {\n");

        sb.append("    state: ").append(toIndentedString(state)).append("\n");
        sb.append("    comment: ").append(toIndentedString(comment)).append("\n");
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

