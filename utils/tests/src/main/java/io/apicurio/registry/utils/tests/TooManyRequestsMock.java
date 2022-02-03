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

package io.apicurio.registry.utils.tests;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

/**
 * @author Fabian Martinez
 */
public class TooManyRequestsMock {

    static final Logger LOGGER = LoggerFactory.getLogger(TooManyRequestsMock.class);

    private WireMockServer server;

    public void start() {
        server = new WireMockServer(
                wireMockConfig()
                        .dynamicPort());
        server.start();

//        kind: "Error"
//        id: "429"
//        code: "SERVICEREGISTRY-429"
//        reason: "Too Many Requests"

        JsonNode body = new ObjectMapper().createObjectNode()
            .put("kind", "Error")
            .put("id", "429")
            .put("code", "SERVICEREGISTRY-429")
            .put("reason", "Too Many Requests");

        server.stubFor(
                any(anyUrl())
                        .willReturn(
                                new ResponseDefinitionBuilder().withStatus(429)
                                    .withJsonBody(body))
                        );
    }

    public String getMockUrl() {
        return server.baseUrl();
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

}