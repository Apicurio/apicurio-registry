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
package io.apicurio.registry.events.http;

import io.apicurio.registry.events.EventSink;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class HttpEventSink implements EventSink {

    private HttpClient httpClient;

    @Inject
    Logger log;

    @Inject
    HttpSinksConfiguration sinksConfiguration;

    @Override
    public String name() {
        return "HTTP Sink";
    }

    @Override
    public boolean isConfigured() {
        return sinksConfiguration.isConfigured();
    }

    @Override
    public void handle(Message<Buffer> message) {

        String type = message.headers().get("type");

        log.info("Firing event " + type);

        for (HttpSinkConfiguration httpSink : sinksConfiguration.httpSinks()) {
            sendEventHttp(type, httpSink, message.body());
        }

    }

    private void sendEventHttp(String type, HttpSinkConfiguration httpSink, Buffer data) {
        try {
            log.debug("Sending event to sink " + httpSink.getName());

            final HttpRequest eventRequest = HttpRequest.newBuilder()
                    .uri(URI.create(httpSink.getEndpoint()))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("ce-id", UUID.randomUUID().toString())
                    .header("ce-specversion", "1.0")
                    .header("ce-source", "apicurio-registry")
                    .header("ce-type", type)
                    .header("content-type", MediaType.APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data.getBytes()))
                    .build();

            final HttpResponse<String> eventResponse = getHttpClient()
                    .send(eventRequest, HttpResponse.BodyHandlers.ofString());

            if (eventResponse.statusCode() != 200) {
                log.warn("Error sending http event: {}", eventResponse.body());
            }


        } catch (Exception e) {
            log.error("Error sending http event", e);
        }
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .build();
        }
        return httpClient;
    }

}