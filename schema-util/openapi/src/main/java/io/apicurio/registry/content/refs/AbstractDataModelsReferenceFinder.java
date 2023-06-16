/*
 * Copyright 2023 Red Hat Inc
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

package io.apicurio.registry.content.refs;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.asyncapi.AsyncApiMessage;
import io.apicurio.datamodels.models.visitors.AllNodeVisitor;
import io.apicurio.registry.content.ContentHandle;

/**
 * Implementation of a reference finder that uses Apicurio Data Models and so supports any specification 
 * contained therein.  Parses the document, finds all $refs, converts them to external references, and 
 * returns them.
 * 
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractDataModelsReferenceFinder implements ReferenceFinder {

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(ContentHandle content) {
        Document doc = Library.readDocumentFromJSONString(content.content());
        
        // Find all the $refs
        RefFinderVisitor visitor = new RefFinderVisitor();
        Library.visitTree(doc, visitor, TraverserDirection.down);
        
        // Convert to ExternalReference and filter.
        return visitor.allReferences.stream()
                .map(ref -> new JsonPointerExternalReference(ref))
                .filter(ref -> ref.getResource() != null)
                .collect(Collectors.toSet());
    }
    
    /**
     * Visitor that will visit every node looking for "$ref" properties.
     * @author eric.wittmann@gmail.com
     */
    private static class RefFinderVisitor extends AllNodeVisitor {
        
        public Set<String> allReferences = new HashSet<>();

        /**
         * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitNode(io.apicurio.datamodels.models.Node)
         */
        @Override
        protected void visitNode(Node node) {
            if (node instanceof Referenceable) {
                String ref = ((Referenceable) node).get$ref();
                if (ref != null && !ref.trim().isEmpty()) {
                    allReferences.add(ref);
                }
            }
        }
        
        /**
         * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitMessage(io.apicurio.datamodels.models.asyncapi.AsyncApiMessage)
         */
        @Override
        public void visitMessage(AsyncApiMessage node) {
            // Note: special handling of message payloads because data-models doesn't fully model the payload yet.
            JsonNode payload = node.getPayload();
            if (payload != null && payload.has("$ref") && !payload.get("$ref").isNull()) {
                String ref = payload.get("$ref").asText();
                allReferences.add(ref);
            }
            super.visitMessage(node);
        }
        
    }

}
