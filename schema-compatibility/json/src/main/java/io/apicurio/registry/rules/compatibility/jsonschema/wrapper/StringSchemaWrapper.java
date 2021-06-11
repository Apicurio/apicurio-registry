/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.FormatValidator;
import org.everit.json.schema.StringSchema;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ToString
public class StringSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final StringSchema wrapped;

    public StringSchemaWrapper(StringSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    public Integer getMinLength() {
        return wrapped.getMinLength();
    }

    public Integer getMaxLength() {
        return wrapped.getMaxLength();
    }

    public FormatValidator getFormatValidator() {
        return wrapped.getFormatValidator();
    }

    public Pattern getPattern() {
        return wrapped.getPattern();
    }

    public Map<String, Object> getUnprocessedProperties() {
        return wrapped.getUnprocessedProperties();
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitStringSchema(this);
    }
}
