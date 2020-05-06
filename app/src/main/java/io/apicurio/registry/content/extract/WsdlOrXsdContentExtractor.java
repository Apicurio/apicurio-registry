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

package io.apicurio.registry.content.extract;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.util.DocumentBuilderAccessor;

/**
 * Performs meta-data extraction for WSDL or XSD content.
 * @author eric.wittmann@gmail.com
 */
public class WsdlOrXsdContentExtractor implements ContentExtractor {
    private static final Logger log = LoggerFactory.getLogger(WsdlOrXsdContentExtractor.class);

    public static final ContentExtractor INSTANCE = new WsdlOrXsdContentExtractor();

    private WsdlOrXsdContentExtractor() {
    }

    public EditableMetaData extract(ContentHandle content) {
        try (InputStream contentIS = content.stream()) {
            Document document = DocumentBuilderAccessor.getDocumentBuilder().parse(contentIS);
            String name = document.getDocumentElement().getAttribute("name");
            String targetNS = document.getDocumentElement().getAttribute("targetNamespace");
            
            EditableMetaData metaData = null;
            if (name != null && !name.equals("")) {
                metaData = new EditableMetaData();
                metaData.setName(name);
            } else if (targetNS != null && !targetNS.equals("")) {
                metaData = new EditableMetaData();
                metaData.setName(targetNS);
            }
            return metaData;
        } catch (Exception e) {
            log.warn("Error extracting metadata from WSDL/XSD: {}", e.getMessage());
            return null;
        }
    }
}
