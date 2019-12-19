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

package io.apicurio.registry.connector;

import java.io.InputStream;
import java.util.Properties;
import javax.enterprise.inject.Vetoed;

/**
 * @author Ales Justin
 */
@Vetoed
public class ConnectorMain {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties(System.getProperties());
        try (InputStream stream = ConnectorMain.class.getResourceAsStream("/application.properties")) {
            properties.load(stream);
        }
        for (String arg : args) {
            String[] split = arg.split("=");
            if (split.length == 2) {
                properties.put(split[0], split[1]);
            }
        }
        ConnectorApplication application = new ConnectorApplication(properties);
        Runtime.getRuntime().addShutdownHook(new Thread(application::stop));
        application.start();
    }
}
