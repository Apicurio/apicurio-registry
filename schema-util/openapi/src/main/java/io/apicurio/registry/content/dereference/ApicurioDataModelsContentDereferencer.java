/*
 * Copyright 2021 Red Hat
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
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.refs.IReferenceResolver;
import io.apicurio.registry.content.ContentHandle;

/**
 * @author eric.wittmann@gmail.com
 */
public class ApicurioDataModelsContentDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        Document document = Library.readDocumentFromJSONString(content.content());
        IReferenceResolver resolver = new RegistryReferenceResolver(resolvedReferences);
        Document dereferencedDoc = Library.dereferenceDocument(document, resolver, false);
        String dereferencedContentStr = Library.writeDocumentToJSONString(dereferencedDoc);
        return ContentHandle.create(dereferencedContentStr);
    }
    
    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.ContentHandle, java.util.Map)
     */
    @Override
    public ContentHandle rewriteReferences(ContentHandle content, Map<String, String> resolvedReferenceUrls) {
        Document doc = Library.readDocumentFromJSONString(content.content());
        ReferenceRewriter visitor = new ReferenceRewriter(resolvedReferenceUrls);
        Library.visitTree(doc, visitor, TraverserDirection.down);
        return ContentHandle.create(Library.writeDocumentToJSONString(doc));
    }
}
