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

import io.apicurio.registry.content.ContentHandle;

/**
 * Canonicalize some content!  This means converting content to its canonical form for
 * the purpose of comparison.  Should remove things like formatting and should sort 
 * fields when ordering is not important.
 * 
 * @author eric.wittmann@gmail.com
 */
public interface ContentCanonicalizer {
    
    /**
     * Called to convert the given content to its canonical form.
     * @param content
     */
    public ContentHandle canonicalize(ContentHandle content);

}
