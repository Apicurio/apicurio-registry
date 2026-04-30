/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseContentCanonicalizerTest {

    @Test
    void returnsOriginalContentWhenCanonicalizationFailsWithExpectedException() {
        TypedContent content = typedContent("original");
        BaseContentCanonicalizer canonicalizer = new ExpectedFailureCanonicalizer();

        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        assertSame(content, result);
    }

    @Test
    void returnsOriginalContentWhenCanonicalizationFailsWithRuntimeException() {
        TypedContent content = typedContent("original");
        BaseContentCanonicalizer canonicalizer = new UnexpectedFailureCanonicalizer();

        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        assertSame(content, result);
    }

    @Test
    void usesCustomErrorHandlerWhenOverridden() {
        TypedContent content = typedContent("original");
        CustomHandlerCanonicalizer canonicalizer = new CustomHandlerCanonicalizer();

        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        assertTrue(canonicalizer.wasHandlerCalled());
        assertEquals("handled", result.getContent().content());
    }

    private static TypedContent typedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    private static class ExpectedFailureCanonicalizer extends BaseContentCanonicalizer {
        @Override
        protected TypedContent doCanonicalize(TypedContent content, Map<String, TypedContent> refs)
                throws ContentCanonicalizationException {
            throw new ContentCanonicalizationException("expected failure");
        }
    }

    private static class UnexpectedFailureCanonicalizer extends BaseContentCanonicalizer {
        @Override
        protected TypedContent doCanonicalize(TypedContent content, Map<String, TypedContent> refs)
                throws ContentCanonicalizationException {
            throw new IllegalStateException("unexpected failure");
        }
    }

    private static class CustomHandlerCanonicalizer extends BaseContentCanonicalizer {
        private boolean handlerCalled;

        @Override
        protected TypedContent doCanonicalize(TypedContent content, Map<String, TypedContent> refs)
                throws ContentCanonicalizationException {
            throw new ContentCanonicalizationException("expected failure");
        }

        @Override
        protected TypedContent handleCanonicalizationError(ContentCanonicalizationException e,
                TypedContent content) {
            handlerCalled = true;
            return typedContent("handled");
        }

        boolean wasHandlerCalled() {
            return handlerCalled;
        }
    }
}
