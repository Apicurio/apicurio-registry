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

import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.slf4j.Logger;
import io.apicurio.registry.events.EventSink;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;

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

    @Inject
    Vertx vertx;

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
            getHttpClient()
                .request(new RequestOptions()
                            .setMethod(HttpMethod.POST)
                            .setURI(httpSink.getEndpoint())
                            .putHeader("ce-id", UUID.randomUUID().toString())
                            .putHeader("ce-specversion", "1.0")
                            .putHeader("ce-source", "apicurio-registry")
                            .putHeader("ce-type", type)
                            .putHeader("content-type", MediaType.APPLICATION_JSON),
                            ar -> {
                                if (ar.succeeded()) {
                                    ar.result()
                                        .exceptionHandler(ex -> log.error("Error sending event to " + httpSink.getEndpoint(), ex))
                                        .end(data);
                                } else {
                                    log.error("Error sending event to " + httpSink.getEndpoint(), ar.cause());
                                }
                            });
        } catch (Exception e) {
            log.error("Error sending http event", e);
        }
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = vertx.createHttpClient(new HttpClientOptions()
                .setConnectTimeout(15 * 1000));
        }
        return httpClient;
    }

}