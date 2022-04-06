/*
 * Copyright 2020 Red Hat Inc
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

import io.apicurio.registry.content.ContentHandle;
import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;
import org.apache.xml.security.parser.XMLParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * A common XML content canonicalizer.
 *
 * @author cfoskin@redhat.com
 */
public class XmlContentCanonicalizer implements ContentCanonicalizer {

    private static ThreadLocal<Canonicalizer> xmlCanonicalizer = new ThreadLocal<Canonicalizer>() {
        @Override protected Canonicalizer initialValue() {
            try {
                return Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS);
            } catch (InvalidCanonicalizerException e) {
                throw new RuntimeException(e);
            }
        }
    };

    static {
        Init.init();
    }

    /**
     * @see ContentCanonicalizer#canonicalize(io.apicurio.registry.content.ContentHandle, Map)
     */
    @Override public ContentHandle canonicalize(ContentHandle content,
            Map<String, ContentHandle> resolvedReferences) {
        try {
            Canonicalizer canon = xmlCanonicalizer.get();
            var out = new ByteArrayOutputStream(content.getSizeBytes());
            canon.canonicalize(content.bytes(), out, false); // TODO secureValidation?
            var canonicalized = out.toString(Canonicalizer.ENCODING);
            return ContentHandle.create(canonicalized);
        } catch (CanonicalizationException | IOException | XMLParserException e) {
        }
        return content;
    }
}
