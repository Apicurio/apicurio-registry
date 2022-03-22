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

import io.apicurio.registry.content.ContentHandle;
import org.apache.avro.Schema;


import java.util.Map;

/**
 * @author carnalca@redhat.com
 */
public class AvroDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        final Schema.Parser parser = new Schema.Parser();
        for (ContentHandle referencedContent : resolvedReferences.values()) {
            parser.parse(referencedContent.content());
        }
        final Schema schema = parser.parse(content.content());
        return ContentHandle.create(schema.toString());
    }
}
