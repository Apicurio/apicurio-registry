/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ObjectSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.Schema;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_BOOLEAN_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_FALSE_TO_TRUE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_TRUE_TO_FALSE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MAX_PROPERTIES_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MAX_PROPERTIES_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MAX_PROPERTIES_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MAX_PROPERTIES_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MIN_PROPERTIES_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MIN_PROPERTIES_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MIN_PROPERTIES_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_MIN_PROPERTIES_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PATTERN_PROPERTY_KEYS_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PATTERN_PROPERTY_KEYS_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PATTERN_PROPERTY_KEYS_MEMBER_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PATTERN_PROPERTY_KEYS_MEMBER_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PATTERN_PROPERTY_KEYS_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_MEMBER_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_MEMBER_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_MEMBER_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_MEMBER_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_SCHEMA_DEPENDENCIES_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_SCHEMA_DEPENDENCIES_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_SCHEMA_DEPENDENCIES_MEMBER_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_SCHEMA_DEPENDENCIES_MEMBER_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_SCHEMA_DEPENDENCIES_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.UNDEFINED_UNUSED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffBooleanTransition;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffInteger;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSetChanged;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSubschemaAddedRemoved;
import static java.util.stream.Collectors.toMap;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class ObjectSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final ObjectSchema original;

    public ObjectSchemaDiffVisitor(DiffContext ctx, ObjectSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitObjectSchema(ObjectSchemaWrapper objectSchema) {
        ctx.log("Visiting " + objectSchema + " at " + objectSchema.getWrapped().getLocation());
        super.visitObjectSchema(objectSchema);
    }

    @Override
    public void visitRequiredPropertyName(String requiredPropName) {
        // this is solved on the higher level
        super.visitRequiredPropertyName(requiredPropName);
    }

    @Override
    public void visitRequiredProperties(List<String> requiredProperties) {
        diffSetChanged(ctx.sub("required"),
            new HashSet<>(original.getRequiredProperties()),
            new HashSet<>(requiredProperties),
            OBJECT_TYPE_REQUIRED_PROPERTIES_ADDED,
            OBJECT_TYPE_REQUIRED_PROPERTIES_REMOVED,
            OBJECT_TYPE_REQUIRED_PROPERTIES_CHANGED,
            OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED,
            OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_REMOVED);
        super.visitRequiredProperties(requiredProperties);
    }

    @Override
    public void visitPropertyNameSchema(SchemaWrapper propertyNameSchema) {
        DiffContext subCtx = ctx.sub("properties");
        if (diffSubschemaAddedRemoved(subCtx, original.getPropertyNameSchema(), propertyNameSchema,
            OBJECT_TYPE_PROPERTY_SCHEMA_ADDED,
            OBJECT_TYPE_PROPERTY_SCHEMA_REMOVED)) {
            propertyNameSchema.accept(new SchemaDiffVisitor(subCtx, original.getPropertyNameSchema()));
        }
        super.visitPropertyNameSchema(propertyNameSchema);
    }

    @Override
    public void visitMinProperties(Integer minProperties) {
        diffInteger(ctx.sub("minProperties"), original.getMinProperties(), minProperties,
            OBJECT_TYPE_MIN_PROPERTIES_ADDED,
            OBJECT_TYPE_MIN_PROPERTIES_REMOVED,
            OBJECT_TYPE_MIN_PROPERTIES_INCREASED,
            OBJECT_TYPE_MIN_PROPERTIES_DECREASED);
        super.visitMinProperties(minProperties);
    }

    @Override
    public void visitMaxProperties(Integer maxProperties) {
        diffInteger(ctx.sub("maxProperties"), original.getMaxProperties(), maxProperties,
            OBJECT_TYPE_MAX_PROPERTIES_ADDED,
            OBJECT_TYPE_MAX_PROPERTIES_REMOVED,
            OBJECT_TYPE_MAX_PROPERTIES_INCREASED,
            OBJECT_TYPE_MAX_PROPERTIES_DECREASED);
        super.visitMaxProperties(maxProperties);
    }

    @Override
    public void visitAllPropertyDependencies(Map<String, Set<String>> propertyDependencies) {
        diffSetChanged(ctx.sub("dependencies"),
            new HashSet<>(original.getPropertyDependencies().keySet()),
            new HashSet<>(propertyDependencies.keySet()),
            OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_ADDED,
            OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_REMOVED,
            OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_CHANGED,
            OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_MEMBER_ADDED,
            OBJECT_TYPE_PROPERTY_DEPENDENCIES_KEYS_MEMBER_REMOVED);
        super.visitAllPropertyDependencies(propertyDependencies);
    }

    @Override
    public void visitPropertyDependencies(String ifPresent, Set<String> allMustBePresent) {
        if (original.getPropertyDependencies().containsKey(ifPresent)) {
            diffSetChanged(ctx.sub("dependencies/" + ifPresent),
                original.getPropertyDependencies().get(ifPresent),
                allMustBePresent,
                UNDEFINED_UNUSED,
                UNDEFINED_UNUSED,
                OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_CHANGED,
                OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_ADDED,
                OBJECT_TYPE_PROPERTY_DEPENDENCIES_VALUE_MEMBER_REMOVED);
        }
        super.visitPropertyDependencies(ifPresent, allMustBePresent);
    }

    @Override
    public void visitAdditionalProperties(boolean permitsAdditionalProperties) {
        diffBooleanTransition(ctx.sub("additionalProperties"), original.permitsAdditionalProperties(), permitsAdditionalProperties, true,
            OBJECT_TYPE_ADDITIONAL_PROPERTIES_FALSE_TO_TRUE,
            OBJECT_TYPE_ADDITIONAL_PROPERTIES_TRUE_TO_FALSE,
            OBJECT_TYPE_ADDITIONAL_PROPERTIES_BOOLEAN_UNCHANGED);
        super.visitAdditionalProperties(permitsAdditionalProperties);
    }

    @Override
    public void visitSchemaOfAdditionalProperties(SchemaWrapper schemaOfAdditionalProperties) {
        DiffContext subCtx = ctx.sub("additionalProperties");
        if (diffSubschemaAddedRemoved(subCtx, original.getSchemaOfAdditionalProperties(), schemaOfAdditionalProperties,
            OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_ADDED,
            OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_REMOVED)) {
            schemaOfAdditionalProperties.accept(new SchemaDiffVisitor(subCtx, original.getSchemaOfAdditionalProperties()));
        }
        super.visitSchemaOfAdditionalProperties(schemaOfAdditionalProperties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void visitPatternProperties(Map<Pattern, SchemaWrapper> patternProperties) {
        diffSetChanged(ctx.sub("patternProperties"),
            original.getPatternProperties().keySet().stream().map(Pattern::toString).collect(Collectors.toSet()),
            patternProperties.keySet().stream().map(Pattern::toString).collect(Collectors.toSet()),
            OBJECT_TYPE_PATTERN_PROPERTY_KEYS_ADDED,
            OBJECT_TYPE_PATTERN_PROPERTY_KEYS_REMOVED,
            OBJECT_TYPE_PATTERN_PROPERTY_KEYS_CHANGED,
            OBJECT_TYPE_PATTERN_PROPERTY_KEYS_MEMBER_ADDED,
            OBJECT_TYPE_PATTERN_PROPERTY_KEYS_MEMBER_REMOVED);
        super.visitPatternProperties(patternProperties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void visitPatternPropertySchema(Pattern propertyNamePattern, SchemaWrapper schema) {
        final Map<String, Schema> stringifiedOriginal = original.getPatternProperties().entrySet().stream()
            .collect(toMap(e -> e.getKey().toString(), Entry::getValue)); // TODO maybe add a wrapper class for Pattern

        if (stringifiedOriginal.containsKey(propertyNamePattern.toString())) {
            schema.accept(new SchemaDiffVisitor(ctx.sub("patternProperties/" + propertyNamePattern),
                stringifiedOriginal.get(propertyNamePattern.toString())));
        }
        super.visitPatternPropertySchema(propertyNamePattern, schema);
    }

    @Override
    public void visitSchemaDependencies(Map<String, SchemaWrapper> schemaDependencies) {
        diffSetChanged(ctx.sub("dependencies"),
            new HashSet<>(original.getSchemaDependencies().keySet()),
            new HashSet<>(schemaDependencies.keySet()),
            OBJECT_TYPE_SCHEMA_DEPENDENCIES_ADDED,
            OBJECT_TYPE_SCHEMA_DEPENDENCIES_REMOVED,
            OBJECT_TYPE_SCHEMA_DEPENDENCIES_CHANGED,
            OBJECT_TYPE_SCHEMA_DEPENDENCIES_MEMBER_ADDED,
            OBJECT_TYPE_SCHEMA_DEPENDENCIES_MEMBER_REMOVED);
        super.visitSchemaDependencies(schemaDependencies);
    }

    @Override
    public void visitSchemaDependency(String propName, SchemaWrapper schema) {
        if (original.getSchemaDependencies().containsKey(propName)) {
            schema.accept(new SchemaDiffVisitor(ctx.sub("dependencies/" + propName),
                original.getSchemaDependencies().get(propName))); // TODO null/invalid schema
        }
        super.visitSchemaDependency(propName, schema);
    }

    @Override
    public void visitPropertySchemas(Map<String, SchemaWrapper> propertySchemas) {
        diffSetChanged(ctx.sub("properties"),
            new HashSet<>(original.getPropertySchemas().keySet()),
            new HashSet<>(propertySchemas.keySet()),
            OBJECT_TYPE_PROPERTY_SCHEMAS_ADDED,
            OBJECT_TYPE_PROPERTY_SCHEMAS_REMOVED,
            OBJECT_TYPE_PROPERTY_SCHEMAS_CHANGED,
            OBJECT_TYPE_PROPERTY_SCHEMAS_MEMBER_ADDED,
            OBJECT_TYPE_PROPERTY_SCHEMAS_MEMBER_REMOVED);
        super.visitPropertySchemas(propertySchemas);
    }

    @Override
    public void visitPropertySchema(String propertyName, SchemaWrapper schema) {
        if (original.getPropertySchemas().containsKey(propertyName)) {
            schema.accept(new SchemaDiffVisitor(ctx.sub("properties/" + propertyName),
                original.getPropertySchemas().get(propertyName))); // TODO null/invalid schema
        }
        super.visitPropertySchema(propertyName, schema);
    }
}
