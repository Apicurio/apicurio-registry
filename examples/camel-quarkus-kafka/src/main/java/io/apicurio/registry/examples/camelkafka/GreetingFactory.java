/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.examples.camelkafka;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("greetingFactory")
public class GreetingFactory {

    private static final String[] NAMES = {
        "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"
    };

    private static final String[] MESSAGES = {
        "Hello from Camel!", "Greetings via Kafka!", "Good day!",
        "Welcome to Apicurio!", "Hi there!", "Howdy!",
        "Salutations!", "Hey, nice to meet you!"
    };

    private int counter = 0;

    public Greeting create() {
        int index = counter++ % NAMES.length;
        return Greeting.newBuilder()
                .setName(NAMES[index])
                .setMessage(MESSAGES[index])
                .setTimestamp(System.currentTimeMillis())
                .build();
    }
}
