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

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.refs.LocalReferenceResolver;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryReferenceResolver extends LocalReferenceResolver {
    
    private final Map<String, ContentHandle> resolvedReferences;

    /**
     * Constructor.
     * @param resolvedReferences
     */
    public RegistryReferenceResolver(Map<String, ContentHandle> resolvedReferences) {
        this.resolvedReferences = resolvedReferences;
    }

    /**
     * @see io.apicurio.datamodels.refs.IReferenceResolver#resolveRef(java.lang.String, io.apicurio.datamodels.models.Node)
     */
    @Override
    public Node resolveRef(String reference, Node from) {
        if (resolvedReferences.containsKey(reference)) {
            ContentHandle resolvedRefContent = resolvedReferences.get(reference);
            Document resolvedRefDoc = Library.readDocumentFromJSONString(resolvedRefContent.content());
            JsonPointerExternalReference ref = new JsonPointerExternalReference(reference);
            return super.resolveRef(ref.getComponent(), resolvedRefDoc);
            // TODO if we find a Node, make sure to modify it by updating all of its $ref values to point to appropriate locations
        }
        
        
        // TODO handle recursive $ref values (refs from refs)
        
        // Cannot resolve the ref, return null.
        return null;
    }

}
