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

package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ArraySchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.BooleanSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.CombinedSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ConditionalSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ConstSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.EmptySchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.EnumSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.FalseSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NotSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NullSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NumberSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ObjectSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ReferenceSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.StringSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.TrueSchemaWrapper;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.CombinedSchema.ValidationCriterion;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public abstract class JsonSchemaWrapperVisitor {

    public void visitSchema(SchemaWrapper schema) {

    }

    public void visitNumberSchema(NumberSchemaWrapper numberSchema) {
        visitSchema(numberSchema);
        visitRequiredInteger(numberSchema.requiresInteger());
        visitExclusiveMinimum(numberSchema.isExclusiveMinimum());
        visitMinimum(numberSchema.getMinimum());
        visitExclusiveMinimumLimit(numberSchema.getExclusiveMinimumLimit());
        visitExclusiveMaximum(numberSchema.isExclusiveMaximum());
        visitMaximum(numberSchema.getMaximum());
        visitExclusiveMaximumLimit(numberSchema.getExclusiveMaximumLimit());
        visitMultipleOf(numberSchema.getMultipleOf());
    }

    public void visitRequiredInteger(boolean requiresInteger) {
    }

    public void visitMinimum(Number minimum) {
    }

    public void visitExclusiveMinimum(boolean exclusiveMinimum) {
    }

    public void visitExclusiveMinimumLimit(Number exclusiveMinimumLimit) {
    }

    public void visitMaximum(Number maximum) {
    }

    public void visitExclusiveMaximum(boolean exclusiveMaximum) {
    }

    public void visitExclusiveMaximumLimit(Number exclusiveMaximumLimit) {
    }

    public void visitMultipleOf(Number multipleOf) {
    }

    public void visit(SchemaWrapper schema) {
        schema.accept(this);
    }

    public void visitArraySchema(ArraySchemaWrapper arraySchema) {
        visitSchema(arraySchema);
        visitMinItems(arraySchema.getMinItems());
        visitMaxItems(arraySchema.getMaxItems());
        visitUniqueItems(arraySchema.needsUniqueItems());
        if (arraySchema.getAllItemSchema() != null) {
            visitAllItemSchema(arraySchema.getAllItemSchema());
        }
        visitAdditionalItems(arraySchema.permitsAdditionalItems());
        if (arraySchema.getItemSchemas() != null) {
            visitItemSchemas(arraySchema.getItemSchemas());
        }
        if (arraySchema.getSchemaOfAdditionalItems() != null) {
            visitSchemaOfAdditionalItems(arraySchema.getSchemaOfAdditionalItems());
        }
        if (arraySchema.getContainedItemSchema() != null) {
            visitContainedItemSchema(arraySchema.getContainedItemSchema());
        }
    }


    public void visitItemSchemas(List<SchemaWrapper> itemSchemas) {
    }

    public void visitMinItems(Integer minItems) {
    }

    public void visitMaxItems(Integer maxItems) {
    }

    public void visitUniqueItems(boolean uniqueItems) {
    }

    public void visitAllItemSchema(SchemaWrapper allItemSchema) {
        visitSchema(allItemSchema);
    }

    public void visitAdditionalItems(boolean additionalItems) {
    }

    public void visitItemSchema(int index, SchemaWrapper itemSchema) {
        visitSchema(itemSchema);
    }

    public void visitSchemaOfAdditionalItems(SchemaWrapper schemaOfAdditionalItems) {
        visitSchema(schemaOfAdditionalItems);
    }

    public void visitContainedItemSchema(SchemaWrapper containedItemSchema) {
        visitSchema(containedItemSchema);
    }

    public void visitBooleanSchema(BooleanSchemaWrapper schema) {
        visitSchema(schema);
    }

    public void visitNullSchema(NullSchemaWrapper nullSchema) {
        visitSchema(nullSchema);
    }

    public void visitEmptySchema(EmptySchemaWrapper emptySchema) {
        visitSchema(emptySchema);
    }

    public void visitConstSchema(ConstSchemaWrapper constSchema) {
        visitSchema(constSchema);
        visitConstValue(constSchema.getPermittedValue());
    }

    /**
     * new method
     */
    public void visitConstValue(Object value) {

    }

    public void visitEnumSchema(EnumSchemaWrapper enumSchema) {
        visitSchema(enumSchema);
        visitEnumValues(enumSchema.getPossibleValues());
    }

    /**
     * new method
     */
    public void visitEnumValues(Set<Object> values) {

    }

    /**
     * new method
     */
    public void visitTrueSchema(TrueSchemaWrapper schema) {
        visitSchema(schema);
    }

    public void visitFalseSchema(FalseSchemaWrapper falseSchema) {
        visitSchema(falseSchema);
    }

    public void visitNotSchema(NotSchemaWrapper notSchema) {
        visitSchema(notSchema);
        visitSchemaMustNotMatch(notSchema.getMustNotMatch());
    }

    /**
     * new method
     */
    public void visitSchemaMustNotMatch(SchemaWrapper mustNotMatch) {

    }

    public void visitReferenceSchema(ReferenceSchemaWrapper referenceSchema) {
        visitSchema(referenceSchema);
        visitReferredSchema(referenceSchema.getReferredSchema());
    }

    /**
     * new method
     */
    public void visitReferredSchema(SchemaWrapper schema) {

    }


    public void visitObjectSchema(ObjectSchemaWrapper objectSchema) {
        visitSchema(objectSchema);
        visitRequiredProperties(objectSchema.getRequiredProperties());
        if (objectSchema.getPropertyNameSchema() != null) {
            visitPropertyNameSchema(objectSchema.getPropertyNameSchema());
        }
        visitMinProperties(objectSchema.getMinProperties());
        visitMaxProperties(objectSchema.getMaxProperties());
        visitAllPropertyDependencies(objectSchema.getPropertyDependencies());
        visitAdditionalProperties(objectSchema.permitsAdditionalProperties());
        if (objectSchema.getSchemaOfAdditionalProperties() != null) {
            visitSchemaOfAdditionalProperties(objectSchema.getSchemaOfAdditionalProperties());
        }
        Map<Pattern, SchemaWrapper> patternProperties = objectSchema.getRegexpPatternProperties();
        if (patternProperties != null) {
            visitPatternProperties(patternProperties);
        }
        visitSchemaDependencies(objectSchema.getSchemaDependencies());
        Map<String, SchemaWrapper> propertySchemas = objectSchema.getPropertySchemas();
        if (propertySchemas != null) {
            visitPropertySchemas(propertySchemas);
        }
    }

    /**
     * new method
     */
    public void visitSchemaDependencies(Map<String, SchemaWrapper> schemaDependencies) {
        for (Entry<String, SchemaWrapper> schemaDep : schemaDependencies.entrySet()) {
            visitSchemaDependency(schemaDep.getKey(), schemaDep.getValue());
        }
    }

    /**
     * new method
     */
    public void visitAllPropertyDependencies(Map<String, Set<String>> propertyDependencies) {
        for (Entry<String, Set<String>> entry : propertyDependencies.entrySet()) {
            visitPropertyDependencies(entry.getKey(), entry.getValue());
        }
    }


    public void visitRequiredProperties(List<String> requiredProperties) {
        for (String requiredPropName : requiredProperties) {
            visitRequiredPropertyName(requiredPropName);
        }
    }

    public void visitPatternProperties(Map<Pattern, SchemaWrapper> patternProperties) {
        for (Entry<Pattern, SchemaWrapper> entry : patternProperties.entrySet()) {
            visitPatternPropertySchema(entry.getKey(), entry.getValue());
        }
    }

    public void visitPropertySchemas(Map<String, SchemaWrapper> propertySchemas) {
    }

    public void visitPropertySchema(String propertyName, SchemaWrapper schema) {
        visitSchema(schema);
    }

    public void visitSchemaDependency(String propKey, SchemaWrapper schema) {
        visitSchema(schema);
    }

    public void visitPatternPropertySchema(Pattern propertyNamePattern, SchemaWrapper schema) {
        visitSchema(schema);
    }

    public void visitSchemaOfAdditionalProperties(SchemaWrapper schemaOfAdditionalProperties) {
        visitSchema(schemaOfAdditionalProperties);
    }

    public void visitAdditionalProperties(boolean additionalProperties) {
    }

    public void visitPropertyDependencies(String ifPresent, Set<String> allMustBePresent) {
    }

    public void visitMaxProperties(Integer maxProperties) {
    }

    public void visitMinProperties(Integer minProperties) {
    }

    public void visitPropertyNameSchema(SchemaWrapper propertyNameSchema) {
        visitSchema(propertyNameSchema);
    }

    public void visitRequiredPropertyName(String requiredPropName) {
    }

    public void visitStringSchema(StringSchemaWrapper stringSchema) {
        visitSchema(stringSchema);
        if (stringSchema.getMinLength() != null)
            visitMinLength(stringSchema.getMinLength());
        if (stringSchema.getMaxLength() != null)
            visitMaxLength(stringSchema.getMaxLength());
        if (stringSchema.getPattern() != null)
            visitPattern(stringSchema.getPattern());
        if (stringSchema.getFormatValidator() != null)
            visitFormat(stringSchema.getFormatValidator().formatName());
    }

    public void visitFormat(String formatName) {
    }

    public void visitPattern(Pattern pattern) {
    }

    public void visitMaxLength(Integer maxLength) {
    }

    public void visitMinLength(Integer minLength) {
    }

    public void visitCombinedSchema(CombinedSchemaWrapper combinedSchema) {
        visitSchema(combinedSchema);
        // Assuming constants, i.e. we can use '==' operator
        final ValidationCriterion criterion = combinedSchema.getCriterion();
        if (CombinedSchema.ALL_CRITERION == criterion) {
            visitAllOfCombinedSchema(combinedSchema);
        } else if (CombinedSchema.ANY_CRITERION == criterion) {
            visitAnyOfCombinedSchema(combinedSchema);
        } else if (CombinedSchema.ONE_CRITERION == criterion) {
            visitOneOfCombinedSchema(combinedSchema);
        } else {
            throw new IllegalStateException("Could not determine if the combined schema is " +
                "'allOf', 'anyOf', or 'oneOf': " + combinedSchema);
        }
    }

    /**
     * new method
     */
    public void visitOneOfCombinedSchema(CombinedSchemaWrapper schema) {

    }

    /**
     * new method
     */
    public void visitAnyOfCombinedSchema(CombinedSchemaWrapper schema) {

    }

    /**
     * new method
     */
    public void visitAllOfCombinedSchema(CombinedSchemaWrapper schema) {

    }


    public void visitConditionalSchema(ConditionalSchemaWrapper conditionalSchema) {
        visitSchema(conditionalSchema);
        conditionalSchema.getIfSchema().ifPresent(this::visitIfSchema);
        conditionalSchema.getThenSchema().ifPresent(this::visitThenSchema);
        conditionalSchema.getElseSchema().ifPresent(this::visitElseSchema);
    }

    public void visitIfSchema(SchemaWrapper ifSchema) {
        visitSchema(ifSchema);
    }

    public void visitThenSchema(SchemaWrapper thenSchema) {
        visitSchema(thenSchema);
    }

    public void visitElseSchema(SchemaWrapper elseSchema) {
        visitSchema(elseSchema);
    }
}
