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

package io.apicurio.registry.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;

import java.io.InputStream;
import java.net.URL;

/**
 * @author Ales Justin
 */
public class HealthUtils {

    public enum Type {
        READY,
        LIVE
    }

    public static void assertHealthCheck(int port, Type type, HealthResponse.Status status) throws Exception {
        URL url = new URL(String.format("http://localhost:%s/health/%s", port, type.name().toLowerCase()));
        try (InputStream stream = url.openStream()) {
            HealthResponse hr = new ObjectMapper().readValue(stream, HealthResponse.class);
            Assertions.assertEquals(status, hr.status);
        }
    }

    public static void assertIsReady(int port) throws Exception {
        assertHealthCheck(port, Type.READY, HealthResponse.Status.UP);
    }

    public static void assertIsLive(int port) throws Exception {
        assertHealthCheck(port, Type.LIVE, HealthResponse.Status.UP);
    }
}
