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

package io.apicurio.registry.util;

/**
 * @author eric.wittmann@gmail.com
 */
public class VersionUtil {

    public static final String toString(Number version) {
        return version != null ? String.valueOf(version) : null;
    }

    public static final Integer toInteger(String version) {
        if (version == null) {
            return null;
        }
        try {
            return Integer.valueOf(version);
        } catch (NumberFormatException e) {
            // TODO what to do here?
            return null;
        }
    }

    public static final Long toLong(String version) {
        if (version == null) {
            return null;
        }
        try {
            return Long.valueOf(version);
        } catch (NumberFormatException e) {
            // TODO what to do here?
            return null;
        }
    }

}
