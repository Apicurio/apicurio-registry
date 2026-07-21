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

import org.apache.camel.builder.RouteBuilder;

public class GreetingRouteBuilder extends RouteBuilder {

    @Override
    public void configure() {
        from("timer:greeting?period=5000")
                .bean("greetingFactory", "create")
                .log("Sending greeting for ${body.name}: ${body.message}")
                .to("kafka:greetings");

        from("kafka:greetings?groupId=camel-greeting-consumer")
                .log("Received greeting — name: ${body.name}, message: ${body.message}, timestamp: ${body.timestamp}");
    }
}
