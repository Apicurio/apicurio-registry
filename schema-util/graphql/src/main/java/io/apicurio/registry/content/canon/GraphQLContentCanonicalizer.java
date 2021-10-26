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

package io.apicurio.registry.content.canon;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.SchemaPrinter.Options;
import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

/**
 * A canonicalizer that handles GraphQL (SDL) formatted content.
 * @author eric.wittmann@gmail.com
 */
public class GraphQLContentCanonicalizer implements ContentCanonicalizer {
    
    private static final SchemaParser sparser = new SchemaParser();
    private static final SchemaGenerator schemaGenerator = new SchemaGenerator();
    private static final RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring().build();
    private static final SchemaPrinter printer = new SchemaPrinter(Options.defaultOptions().includeDirectives(false));
    
    /**
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            TypeDefinitionRegistry typeRegistry = sparser.parse(content.content());
            String canonicalized = printer.print(schemaGenerator.makeExecutableSchema(typeRegistry, wiring));
            return ContentHandle.create(canonicalized);
        } catch (Exception e) {
            // Must not be a GraphQL file
        }
        return content;
    }

}
