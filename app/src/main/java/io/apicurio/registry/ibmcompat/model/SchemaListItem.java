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

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.Objects;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SchemaListItem extends SchemaSummary {

    private SchemaVersion latest;

    /**
     *
     **/

    @JsonProperty("latest")
    @NotNull
    public SchemaVersion getLatest() {
        return latest;
    }

    public void setLatest(SchemaVersion latest) {
        this.latest = latest;
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) return false;
        SchemaListItem schemaListItem = (SchemaListItem) o;
        return Objects.equals(latest, schemaListItem.latest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), latest);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaListItem {\n");

        sb.append(super.toString()).append("\n");
        sb.append("    latest: ").append(toIndentedString(latest)).append("\n");
        sb.append("}");
        return sb.toString();
    }
}

