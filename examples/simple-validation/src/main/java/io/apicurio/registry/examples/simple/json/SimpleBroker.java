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

package io.apicurio.registry.examples.simple.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.apicurio.registry.utils.IoUtil;

/**
 * @author eric.wittmann@gmail.com
 */
public class SimpleBroker {

    private static final int port = 12345;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<String> messages = Collections.synchronizedList(new LinkedList());
    private int getCursor = 0;

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/", httpExchange -> {
                if (httpExchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    handleGet(httpExchange);
                } else if (httpExchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    handlePost(httpExchange);
                } else {
                    handleDefault(httpExchange);
                }
            });

            server.start();
        } catch (Throwable tr) {
            tr.printStackTrace();
        }
    }

    /**
     * @param httpExchange
     */
    private void handleGet(HttpExchange httpExchange) throws IOException {
        if (getCursor < this.messages.size()) {
            byte [] response = this.messages.get(getCursor++).getBytes(StandardCharsets.UTF_8);

            httpExchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            httpExchange.sendResponseHeaders(200, response.length);

            OutputStream out = httpExchange.getResponseBody();
            out.write(response);
            out.close();
        } else {
            httpExchange.sendResponseHeaders(404, 0);
            httpExchange.close();
        }
    }

    /**
     * @param httpExchange
     */
    private void handlePost(HttpExchange httpExchange) throws IOException {
        try (InputStream bodyIS = httpExchange.getRequestBody()) {
            String message = IoUtil.toString(bodyIS);
            this.messages.add(message);
            System.out.println("Received message!");

            httpExchange.sendResponseHeaders(201, 0);
            httpExchange.close();
        }
    }

    /**
     * @param httpExchange
     */
    private void handleDefault(HttpExchange httpExchange) throws IOException {
        byte response[] = "Operation not supported".getBytes("UTF-8");

        httpExchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        httpExchange.sendResponseHeaders(500, response.length);

        OutputStream out = httpExchange.getResponseBody();
        out.write(response);
        out.close();
    }

}
