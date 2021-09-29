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

package io.apicurio.tests.utils;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Fabian Martinez
 */
public class RateLimitingProxy {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private Vertx vertx;
    private int port = 30001;

    private HttpServer server;

    private HttpClient client;
    private String destinationHost;
    private int destinationPort;

    private int buckets;

    public RateLimitingProxy(int failAfterRequests, String destinationHost, int destinationPort) {

        // this will rate limit just based on total requests
        // that means that if buckets=3 the proxy will successfully redirect the first 3 requests and every request after that will be rejected with 429 status
        this.buckets = failAfterRequests;

        vertx = Vertx.vertx();
        client = vertx.createHttpClient(new HttpClientOptions());
        if (destinationHost.endsWith("127.0.0.1.nip.io")) {
            logger.info("Changing proxy destination host to localhost");
            this.destinationHost = "localhost";
        } else {
            this.destinationHost = destinationHost;
        }
        this.destinationPort = destinationPort;
    }

    public String getServerUrl() {
        return "http://localhost:" + port;
    }

    public void start() {

        server = vertx.createHttpServer(new HttpServerOptions().setPort(port)).requestHandler(this::proxyRequest)
                .listen(server -> {
                    if (server.succeeded()) {
                        logger.info("Proxy server started on port {}", port);
                        logger.info("Proxying server {}:{}", destinationHost, destinationPort);
                    } else {
                        logger.error("Error starting server", server.cause());
                    }
                });

    }

    public void stop() {
        if (server != null) {
            server.close();
        }
    }

    private synchronized boolean allowed() {
        if (buckets > 0) {
            buckets--;
            return true;
        }
        return false;
    }

    private void proxyRequest(HttpServerRequest req) {

        boolean allowed = allowed();

        if (!allowed) {
            logger.info("Rejecting request, no longer allowed");
            req.response().setStatusCode(429);
            req.response().end();
            return;
        }
        logger.info("Allowing request, redirecting");

        client.request(req.method(), destinationPort, destinationHost, req.uri())
                .onSuccess(clientReq -> executeProxy(clientReq, req))
                .onFailure(throwable -> logger.error("Error found creating request", throwable));
    }

    private void executeProxy(HttpClientRequest clientReq, HttpServerRequest serverRequest) {

        clientReq.response(reqResult -> {
            if (reqResult.succeeded()) {
                HttpClientResponse clientResponse = reqResult.result();
                serverRequest.response().setChunked(true);
                serverRequest.response().setStatusCode(clientResponse.statusCode());
                serverRequest.response().headers().setAll(clientResponse.headers());
                clientResponse.handler(data -> serverRequest.response().write(data));
                clientResponse.endHandler((v) -> serverRequest.response().end());
                clientResponse.exceptionHandler(e -> logger.error("Error caught in response of request to serverless", e));
                serverRequest.response().exceptionHandler(e -> logger.error("Error caught in response to client", e));
            }
        });

        if (serverRequest.isEnded()) {
            clientReq.end();
        } else {
            serverRequest.handler(clientReq::write);
            serverRequest.endHandler((v) -> clientReq.end());
            clientReq.exceptionHandler(e -> {
                logger.error("Error caught in proxiying request", e);
                serverRequest.response().setStatusCode(500).putHeader("x-error", e.getMessage()).end();
            });
        }

        serverRequest.exceptionHandler(e -> {
            logger.error("Error caught in request from client", e);
        });
    }
}
