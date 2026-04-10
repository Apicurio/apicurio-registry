/*
 * Copyright 2022 Red Hat
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

package io.apicurio.schema.validation.json;

import java.util.List;

/**
 * @author Fabian Martinez
 */
public class JsonValidationResult {

    protected static final JsonValidationResult SUCCESS = successful();

    private boolean success;
    private List<ValidationError> validationErrors;

    private JsonValidationResult(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
        this.success = this.validationErrors == null || this.validationErrors.isEmpty();
    }

    public boolean success() {
        return success;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    @Override
    public String toString() {
        if (this.success) {
            return "JsonValidationResult [ success ]";
        } else {
            return "JsonValidationResult [ errors = " + validationErrors.toString() + " ]";
        }
    }

    public static JsonValidationResult fromErrors(List<ValidationError> errors) {
        return new JsonValidationResult(errors);
    }

    public static JsonValidationResult successful() {
        return new JsonValidationResult(null);
    }

}
