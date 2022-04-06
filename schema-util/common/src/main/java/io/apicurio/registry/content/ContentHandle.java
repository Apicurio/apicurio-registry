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

package io.apicurio.registry.content;

import java.io.InputStream;

/**
 * @author Ales Justin
 */
public interface ContentHandle {

    static ContentHandle create(InputStream stream) {
        return new StreamContentHandle(stream);
    }

    static ContentHandle create(byte[] bytes) {
        return new BytesContentHandle(bytes);
    }

    static ContentHandle create(String content) {
        return new StringContentHandle(content);
    }

    InputStream stream();

    byte[] bytes();

    String content();

    int getSizeBytes();
}
