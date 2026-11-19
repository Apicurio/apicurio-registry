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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

public class GreetingRouteBuilder extends RouteBuilder {

    @Override
    public void configure() {
        from("timer:greeting?period={{greeting.period}}")
                .bean("greetingFactory", "create")
                .setHeader(KafkaConstants.KEY, simple("${body.name}"))
                .log("Sending greeting for ${body.name}: ${body.message}")
                .to("kafka:greetings");

        // Sends a greeting with the name and message given as query parameters
        from("platform-http:/greetings/send?httpMethodRestrict=POST")
                .bean("greetingFactory", "createFor(${header.name}, ${header.message})")
                .setHeader(KafkaConstants.KEY, simple("${body.name}"))
                .log("Sending greeting for ${body.name}: ${body.message}")
                .to("kafka:greetings")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204));

        from("kafka:greetings?groupId=camel-greeting-consumer")
                .log("Received greeting — name: ${body.name}, message: ${body.message}, timestamp: ${body.timestamp}")
                .bean("receivedGreetings", "addGreeting");

        // The same records without the Avro deserializer: the endpoint option overrides the component's
        from("kafka:greetings?groupId=camel-greeting-raw"
                + "&valueDeserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer")
                .bean("receivedGreetings", "addRaw(${header." + KafkaConstants.KEY + "}, ${body})");

        from("platform-http:/greetings/received?httpMethodRestrict=GET")
                .bean("receivedGreetings", "greetings")
                .marshal().json();

        from("platform-http:/greetings/raw?httpMethodRestrict=GET")
                .bean("receivedGreetings", "raw")
                .marshal().json();
    }
}
