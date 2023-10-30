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

package io.apicurio.registry.content.refs;

/**
 * @author eric.wittmann@gmail.com
 */
public class JsonPointerExternalReference extends ExternalReference {

    /**
     * Constructor.
     * @param jsonPointer
     */
    public JsonPointerExternalReference(String jsonPointer) {
        super(jsonPointer, resourceFrom(jsonPointer), componentFrom(jsonPointer));
    }

    private static String componentFrom(String jsonPointer) {
        int idx = jsonPointer.indexOf('#');
        if (idx == 0) {
            return jsonPointer;
        } else if (idx > 0) {
            return jsonPointer.substring(idx);
        } else {
            return null;
        }
    }

    private static String resourceFrom(String jsonPointer) {
        int idx = jsonPointer.indexOf('#');
        if (idx == 0) {
            return null;
        } else if (idx > 0) {
            return jsonPointer.substring(0, idx);
        } else {
            return jsonPointer;
        }
    }

}
