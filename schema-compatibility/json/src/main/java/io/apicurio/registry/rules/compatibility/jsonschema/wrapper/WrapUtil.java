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

import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.BooleanSchema;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.ConditionalSchema;
import org.everit.json.schema.ConstSchema;
import org.everit.json.schema.EmptySchema;
import org.everit.json.schema.EnumSchema;
import org.everit.json.schema.FalseSchema;
import org.everit.json.schema.NotSchema;
import org.everit.json.schema.NullSchema;
import org.everit.json.schema.NumberSchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.ReferenceSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.StringSchema;
import org.everit.json.schema.TrueSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class WrapUtil {


    public static SchemaWrapper wrap(Schema schema) {
        if (schema == null)
            return null;

        if (schema instanceof ObjectSchema) {
            return new ObjectSchemaWrapper((ObjectSchema) schema);
        } else if (schema instanceof ArraySchema) {
            return new ArraySchemaWrapper((ArraySchema) schema);
        } else if (schema instanceof StringSchema) {
            return new StringSchemaWrapper((StringSchema) schema);
        } else if (schema instanceof EmptySchema && !(schema instanceof TrueSchema)) {
            return new EmptySchemaWrapper((EmptySchema) schema);
        } else if (schema instanceof TrueSchema) {
            return new TrueSchemaWrapper((TrueSchema) schema);
        } else if (schema instanceof FalseSchema) {
            return new FalseSchemaWrapper((FalseSchema) schema);
        } else if (schema instanceof BooleanSchema) {
            return new BooleanSchemaWrapper((BooleanSchema) schema);
        } else if (schema instanceof ConstSchema) {
            return new ConstSchemaWrapper((ConstSchema) schema);
        } else if (schema instanceof EnumSchema) {
            return new EnumSchemaWrapper((EnumSchema) schema);
        } else if (schema instanceof NullSchema) {
            return new NullSchemaWrapper((NullSchema) schema);
        } else if (schema instanceof NotSchema) {
            return new NotSchemaWrapper((NotSchema) schema);
        } else if (schema instanceof ReferenceSchema) {
            return new ReferenceSchemaWrapper((ReferenceSchema) schema);
        } else if (schema instanceof CombinedSchema) {
            return new CombinedSchemaWrapper((CombinedSchema) schema);
        } else if (schema instanceof ConditionalSchema) {
            return new ConditionalSchemaWrapper((ConditionalSchema) schema);
        } else if (schema instanceof NumberSchema) {
            return new NumberSchemaWrapper((NumberSchema) schema);
        } else {
            throw new IllegalStateException("No wrapper for an underlying schema type '" + schema.getClass() + "': " + schema);
        }
    }

    public static List<SchemaWrapper> wrap(List<Schema> itemSchemas) {
        if (itemSchemas == null)
            return null;
        return itemSchemas.stream().map(WrapUtil::wrap).collect(Collectors.toList());
    }

    public static <K> Map<K, SchemaWrapper> wrap(Map<K, Schema> map) {
        requireNonNull(map);
        return map.entrySet().stream()
            //.map(entry -> new SimpleEntry<>(entry.getKey(), wrap(entry.getValue())))
            .collect(toMap(
                Entry::getKey,
                e -> wrap(e.getValue())
            ));
    }

    public static Collection<SchemaWrapper> wrap(Collection<Schema> subschemas) {
        return wrap(new ArrayList<>(subschemas));
    }

    public static Optional<SchemaWrapper> wrap(Optional<Schema> schema) {
        return schema.map(WrapUtil::wrap);
    }

    public static EqualitySchemaWrapper equalityWrap(Schema wrapped) {
        return new EqualitySchemaWrapper(wrapped);
    }
}
