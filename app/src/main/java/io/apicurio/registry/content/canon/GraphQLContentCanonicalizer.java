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

package io.apicurio.registry.content.canon;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.apicurio.registry.content.ContentCanonicalizer;
import io.apicurio.registry.content.ContentHandle;

/**
 * A canonicalizer that handles GraphQL (SDL) formatted content.
 * @author eric.wittmann@gmail.com
 */
public class GraphQLContentCanonicalizer implements ContentCanonicalizer {
    
    /**
     * @see io.apicurio.registry.content.ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ContentHandle canonicalize(ContentHandle content) {
        try {
            TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(content.content());
            SchemaGenerator schemaGenerator = new SchemaGenerator();
            String canonicalized = new SchemaPrinter().print(schemaGenerator.makeExecutableSchema(typeRegistry, RuntimeWiring.newRuntimeWiring().build()));
            return ContentHandle.create(canonicalized);
        } catch (Exception e) {
            // Must not be a GraphQL file
        }
        return content;
    }

}
