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

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.visitors.AllNodeVisitor;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Inlines all references found in the data model.
 * @author eric.wittmann@gmail.com
 */
public class ReferenceInliner extends AllNodeVisitor {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceInliner.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, ContentHandle> resolvedReferences;

    /**
     * Constructor.
     * @param resolvedReferences
     */
    public ReferenceInliner(Map<String, ContentHandle> resolvedReferences) {
        this.resolvedReferences = resolvedReferences;
    }

    /**
     * @see AllNodeVisitor#visitNode(Node)
     */
    @Override
    protected void visitNode(Node node) {
        if (node instanceof Referenceable) {
            String $ref = ((Referenceable) node).get$ref();
            if ($ref != null && resolvedReferences.containsKey($ref)) {
                inlineRef((Referenceable) node);
            }
        }
    }

    /**
     * Inlines the given reference.
     * @param refNode
     */
    private void inlineRef(Referenceable refNode) {
        String $ref = refNode.get$ref();

        JsonPointerExternalReference refPointer = new JsonPointerExternalReference($ref);
        ContentHandle refContent = resolvedReferences.get($ref);

        // Get the specific node within the content that this $ref points to
        ObjectNode refContentNode = getRefNodeFromContent(refContent, refPointer.getComponent());
        if (refContentNode != null) {
            // Read that content *into* the current node
            Library.readNode(refContentNode, (Node) refNode);

            // Set the $ref to null (now that we've inlined the referenced node)
            refNode.set$ref(null);
        }
    }

    private ObjectNode getRefNodeFromContent(ContentHandle refContent, String refComponent) {
        try {
            ObjectNode refContentRootNode = (ObjectNode) mapper.readTree(refContent.content());
            if (refComponent != null) {
                JsonPointer pointer = JsonPointer.compile(refComponent.substring(1));
                JsonNode nodePointedTo = refContentRootNode.at(pointer);
                if (!nodePointedTo.isMissingNode() && nodePointedTo.isObject()) {
                    return (ObjectNode) nodePointedTo;
                }
            } else {
                return refContentRootNode;
            }
        } catch (Exception e) {
            logger.error("Failed to get referenced node from $ref content.", e);
        }
        return null;
    }

}
