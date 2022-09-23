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

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.CombinedSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ObjectSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.StringSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_BOOLEAN_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_EXTENDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_FALSE_TO_TRUE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_NARROWED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_UNCHANGED;
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
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_EXTENDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.OBJECT_TYPE_PROPERTY_SCHEMAS_NARROWED;
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
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSchemaOrTrue;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSetChanged;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSubSchemasAdded;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSubSchemasRemoved;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSubschemaAddedRemoved;
import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;
import static java.util.stream.Collectors.toMap;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class ObjectSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final ObjectSchema original;
    private ObjectSchemaWrapper schema;

    public ObjectSchemaDiffVisitor(DiffContext ctx, ObjectSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitObjectSchema(ObjectSchemaWrapper objectSchema) {
        ctx.log("Visiting " + objectSchema + " at " + objectSchema.getWrapped().getLocation());
        this.schema = objectSchema;
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
        if (diffBooleanTransition(ctx.sub("additionalProperties"), original.permitsAdditionalProperties(), permitsAdditionalProperties, true,
                OBJECT_TYPE_ADDITIONAL_PROPERTIES_FALSE_TO_TRUE,
                OBJECT_TYPE_ADDITIONAL_PROPERTIES_TRUE_TO_FALSE,
                OBJECT_TYPE_ADDITIONAL_PROPERTIES_BOOLEAN_UNCHANGED)) {

            if (permitsAdditionalProperties) {
                Schema updatedAdditionalProperties = schema.getSchemaOfAdditionalProperties() == null ? null :
                        schema.getSchemaOfAdditionalProperties().getWrapped();
                diffSchemaOrTrue(ctx.sub("schemaOfAdditionalItems"), original.getSchemaOfAdditionalProperties(),
                        updatedAdditionalProperties, OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_UNCHANGED,
                        OBJECT_TYPE_ADDITIONAL_PROPERTIES_EXTENDED, OBJECT_TYPE_ADDITIONAL_PROPERTIES_NARROWED,
                        OBJECT_TYPE_ADDITIONAL_PROPERTIES_SCHEMA_CHANGED);
            }
        }
        super.visitAdditionalProperties(permitsAdditionalProperties);
    }

    @Override
    public void visitSchemaOfAdditionalProperties(SchemaWrapper schemaOfAdditionalProperties) {
        // This is also handled by visitAdditionalProperties
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
        @SuppressWarnings("serial")
        Set<String> allPropertySchemaNames = new HashSet<String>() {{
            addAll(original.getPropertySchemas().keySet());
            addAll(schema.getPropertySchemas().keySet());
        }};

        List<SchemaWrapper> addedPropertySchemas = new ArrayList<>();
        List<SchemaWrapper> removedPropertySchemas = new ArrayList<>();
        for (String propertySchemaName: allPropertySchemaNames) {
            boolean existInOriginal = original.getPropertySchemas().containsKey(propertySchemaName);
            boolean existInUpdated = propertySchemas.containsKey(propertySchemaName);
            if (!existInOriginal && existInUpdated) {
                // adding properties
                addedPropertySchemas.add(propertySchemas.get(propertySchemaName));
            } else if (existInOriginal && !existInUpdated) {
                // removing properties
                removedPropertySchemas.add(wrap(original.getPropertySchemas().get(propertySchemaName)));
            } else if (existInOriginal && existInUpdated) {
                visitPropertySchema(propertySchemaName, propertySchemas.get(propertySchemaName));
            }
        }
        if (!addedPropertySchemas.isEmpty()) {
            diffSubSchemasAdded(ctx.sub("propertySchemasAdded"), addedPropertySchemas,
                    original.permitsAdditionalProperties(), wrap(original.getSchemaOfAdditionalProperties()),
                    schema.permitsAdditionalProperties(), OBJECT_TYPE_PROPERTY_SCHEMAS_EXTENDED,
                    OBJECT_TYPE_PROPERTY_SCHEMAS_NARROWED, OBJECT_TYPE_PROPERTY_SCHEMAS_CHANGED);
        }
        if (!removedPropertySchemas.isEmpty()) {
            diffSubSchemasRemoved(ctx.sub("propertySchemasRemoved"), removedPropertySchemas,
                    schema.permitsAdditionalProperties(), schema.getSchemaOfAdditionalProperties(),
                    original.permitsAdditionalProperties(), OBJECT_TYPE_PROPERTY_SCHEMAS_NARROWED,
                    OBJECT_TYPE_PROPERTY_SCHEMAS_EXTENDED, OBJECT_TYPE_PROPERTY_SCHEMAS_CHANGED);
        }
        super.visitPropertySchemas(propertySchemas);
    }

    @Override
    public void visitPropertySchema(String propertyName, SchemaWrapper schema) {
        if (original.getPropertySchemas().containsKey(propertyName)) {
            Schema originalPropertySchema = original.getPropertySchemas().get(propertyName);
            if (originalPropertySchema instanceof StringSchema
                && schema instanceof CombinedSchemaWrapper) {
                originalPropertySchema = CombinedSchema
                    .builder()
                    .criterion(CombinedSchema.ANY_CRITERION)
                    .subschema(originalPropertySchema)
                    .build();
            }

            schema.accept(new SchemaDiffVisitor(ctx.sub("properties/" + propertyName),
                originalPropertySchema)); // TODO null/invalid schema
        }
        super.visitPropertySchema(propertyName, schema);
    }
}
