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

package io.apicurio.registry.content.dereference;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.asyncapi.AsyncApiMessage;
import io.apicurio.datamodels.models.visitors.AllNodeVisitor;

/**
 * Rewrites all references in a data model using a map of replacements provided.
 * @author eric.wittmann@gmail.com
 */
public class ReferenceRewriter extends AllNodeVisitor {
    
    private final Map<String, String> referenceUrls;

    /**
     * Constructor.
     * @param resolvedReferenceUrls
     */
    public ReferenceRewriter(Map<String, String> referenceUrls) {
        this.referenceUrls = referenceUrls;
    }

    /**
     * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitNode(io.apicurio.datamodels.models.Node)
     */
    @Override
    protected void visitNode(Node node) {
        if (node instanceof Referenceable) {
            String $ref = ((Referenceable) node).get$ref();
            if ($ref != null && referenceUrls.containsKey($ref)) {
                ((Referenceable) node).set$ref(referenceUrls.get($ref));
            }
        }
    }
    
    /**
     * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitMessage(io.apicurio.datamodels.models.asyncapi.AsyncApiMessage)
     */
    @Override
    public void visitMessage(AsyncApiMessage node) {
        super.visitMessage(node);
        
        // Note: for now we have special handling of the payload because it's not yet fully modeled in the
        // apicurio-data-models library.
        JsonNode payload = node.getPayload();
        if (payload != null && payload.hasNonNull("$ref")) {
            String $ref = payload.get("$ref").asText();
            if (referenceUrls.containsKey($ref)) {
                ((ObjectNode) payload).put("$ref", referenceUrls.get($ref)); 
            }
        }
    }

}
