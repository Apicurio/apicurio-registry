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

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @author eric.wittmann@gmail.com
 */
public class MessagePublisher {
    private static final HttpClient httpClient;
    private static final String BROKER_URL = "http://localhost:12345";

    static {
        httpClient = HttpClient.newHttpClient();
    }

    /**
     * Publishes a message to the mock broker.
     *
     * @param message the message to publish
     */
    public void publishMessage(MessageBean message) {
        try {
            JSONObject messageObj = new JSONObject(message);
            String data = messageObj.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BROKER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Produced message: " + message);
            } else {
                System.err.println("Failed to publish message. Status: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Error publishing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
