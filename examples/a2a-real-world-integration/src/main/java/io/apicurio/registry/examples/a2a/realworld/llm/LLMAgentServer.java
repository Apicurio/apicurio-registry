/*
 * Copyright 2025 Red Hat Inc
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
package io.apicurio.registry.examples.a2a.realworld.llm;

import io.apicurio.registry.examples.a2a.realworld.agents.MockAgentServer;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A2A Agent Server powered by local LLM via Ollama.
 *
 * This class extends MockAgentServer to provide real AI-powered responses
 * instead of mock/simulated data. Each LLMAgentServer instance:
 *
 * - Connects to a local Ollama instance
 * - Uses a specialized system prompt for its domain
 * - Returns structured JSON responses
 * - Implements the full A2A protocol (discovery + task execution)
 */
public class LLMAgentServer extends MockAgentServer {

    private static final Logger LOGGER = Logger.getLogger(LLMAgentServer.class.getName());

    private final OllamaClient ollamaClient;
    private final String systemPrompt;

    /**
     * Create a new LLM-powered A2A agent server.
     *
     * @param port The port to listen on
     * @param agentName The name of the agent (shown in agent card)
     * @param agentDescription Description of agent capabilities
     * @param skills Array of skill identifiers
     * @param ollamaClient The Ollama client for LLM calls
     * @param systemPrompt The system prompt defining agent behavior
     */
    public LLMAgentServer(int port, String agentName, String agentDescription,
                          String[] skills, OllamaClient ollamaClient, String systemPrompt) {
        super(port, agentName, agentDescription, skills, null);
        this.ollamaClient = ollamaClient;
        this.systemPrompt = systemPrompt;

        // Set the task handler to use the LLM
        this.taskHandler = this::processWithLLM;
    }

    /**
     * Process a task using the Ollama LLM.
     *
     * @param userMessage The user's input message
     * @return The LLM-generated response (JSON format)
     */
    private String processWithLLM(String userMessage) {
        try {
            LOGGER.info("[" + agentName + "] Calling Ollama " + ollamaClient.getModel() + "...");

            long startTime = System.currentTimeMillis();
            String response = ollamaClient.generate(userMessage, systemPrompt);
            long duration = System.currentTimeMillis() - startTime;

            LOGGER.info("[" + agentName + "] LLM response received in " + duration + "ms");

            // Validate it's valid JSON
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
            } catch (Exception e) {
                LOGGER.warning("[" + agentName + "] LLM returned invalid JSON, wrapping response");
                response = "{\"response\": " +
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(response) + "}";
            }

            return response;

        } catch (Exception e) {
            LOGGER.severe("[" + agentName + "] LLM error: " + e.getMessage());
            return "{\"error\": \"LLM processing failed: " +
                    e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    @Override
    public void start() throws IOException {
        LOGGER.info("[" + agentName + "] Starting LLM-powered agent on port " + port +
                " (model: " + ollamaClient.getModel() + ")");
        super.start();
    }

    /**
     * Get the Ollama client used by this agent.
     */
    public OllamaClient getOllamaClient() {
        return ollamaClient;
    }

    /**
     * Get the system prompt used by this agent.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
}
