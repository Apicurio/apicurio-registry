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
package io.apicurio.registry.utils.embedded;

import io.smallrye.config.PropertiesConfigSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

/**
 * @author Ales Justin
 */
public class EmbeddedRegistryConfigSource extends PropertiesConfigSource {
    public EmbeddedRegistryConfigSource() throws IOException {
        super(readProperties(), "EmbeddedRegistry");
    }

    private static Properties readProperties() throws IOException {
        Properties properties = new Properties();
        Enumeration<URL> appProps = EmbeddedRegistryConfigSource.class.getClassLoader().getResources("application.properties");
        while (appProps.hasMoreElements()) {
            mergeApplicationProperties(properties, appProps.nextElement());
        }
        return properties;
    }

    private static void mergeApplicationProperties(Properties merged, URL url) throws IOException {
        Properties properties = new Properties();
        try (InputStream is = url.openStream()) {
            properties.load(is);
        }
        merged.putAll(properties);
    }
}
