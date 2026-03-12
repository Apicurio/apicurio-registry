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
package io.apicurio.registry.examples.a2a.realworld.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator.ContextualStep;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator.WorkflowResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Simple web server providing an HTML UI for submitting customer complaints
 * to the context chaining workflow.
 */
public class WebUIServer {

    private static final Logger LOGGER = Logger.getLogger(WebUIServer.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final int port;
    private final int sentimentPort;
    private final int analyzerPort;
    private final int responsePort;
    private final int translatorPort;
    private HttpServer server;

    public WebUIServer(int port, int sentimentPort, int analyzerPort,
                       int responsePort, int translatorPort) {
        this.port = port;
        this.sentimentPort = sentimentPort;
        this.analyzerPort = analyzerPort;
        this.responsePort = responsePort;
        this.translatorPort = translatorPort;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // Serve the HTML UI
        server.createContext("/", this::handleIndex);

        // API endpoint for processing complaints
        server.createContext("/api/process", this::handleProcess);

        server.start();
        LOGGER.info("Web UI started at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("Web UI stopped");
        }
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String html = getIndexHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleProcess(HttpExchange exchange) throws IOException {
        // Handle CORS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Read request body
        String requestBody;
        try (InputStream is = exchange.getRequestBody()) {
            requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        JsonNode request = objectMapper.readTree(requestBody);
        String complaint = request.has("complaint") ? request.get("complaint").asText() : "";

        if (complaint.isBlank()) {
            sendJsonResponse(exchange, 400, "{\"error\": \"Complaint text is required\"}");
            return;
        }

        LOGGER.info("Processing complaint: " + complaint.substring(0, Math.min(50, complaint.length())) + "...");

        try {
            // Execute the context chaining workflow
            ObjectNode result = executeWorkflow(complaint);
            String responseJson = objectMapper.writeValueAsString(result);
            sendJsonResponse(exchange, 200, responseJson);
        } catch (Exception e) {
            LOGGER.warning("Workflow failed: " + e.getMessage());
            sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private ObjectNode executeWorkflow(String complaint) throws Exception {
        List<ContextualStep> steps = List.of(
            new ContextualStep(
                "Sentiment Analysis",
                "http://localhost:" + sentimentPort,
                "sentiment",
                "{{original}}"
            ),
            new ContextualStep(
                "Issue Analysis",
                "http://localhost:" + analyzerPort,
                "analysis",
                """
                CUSTOMER MESSAGE:
                {{original}}

                SENTIMENT ANALYSIS:
                {{sentiment}}

                Based on the sentiment, perform issue extraction and prioritization.
                """
            ),
            new ContextualStep(
                "Response Generation",
                "http://localhost:" + responsePort,
                "response",
                """
                CUSTOMER MESSAGE:
                {{original}}

                SENTIMENT ANALYSIS:
                {{sentiment}}

                ISSUE ANALYSIS:
                {{analysis}}

                Generate an empathetic response addressing all issues.
                """
            ),
            new ContextualStep(
                "Translation",
                "http://localhost:" + translatorPort,
                "translation",
                """
                Translate to Spanish:

                {{response}}
                """
            )
        );

        A2AOrchestrator orchestrator = new A2AOrchestrator("http://localhost:8080");
        List<WorkflowResult> results = orchestrator.executeContextualWorkflow(
            "Customer Complaint Pipeline",
            steps,
            complaint
        );

        // Build response JSON
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", results.stream().allMatch(r -> r.success));
        response.put("totalDurationMs", results.stream().mapToLong(r -> r.durationMs).sum());

        ArrayNode stepsArray = response.putArray("steps");
        for (WorkflowResult result : results) {
            ObjectNode stepNode = stepsArray.addObject();
            stepNode.put("name", result.stepName);
            stepNode.put("success", result.success);
            stepNode.put("durationMs", result.durationMs);

            // Try to parse response as JSON, otherwise use as string
            try {
                JsonNode parsed = objectMapper.readTree(result.response);
                stepNode.set("output", parsed);
            } catch (Exception e) {
                stepNode.put("output", result.response);
            }
        }

        return response;
    }

    private void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getIndexHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>A2A Context Chaining Demo</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
            min-height: 100vh;
            color: #e0e0e0;
            padding: 20px;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        h1 {
            text-align: center;
            margin-bottom: 10px;
            color: #00d4ff;
            font-size: 2rem;
        }
        .subtitle {
            text-align: center;
            color: #888;
            margin-bottom: 30px;
        }
        .pipeline-info {
            background: rgba(0, 212, 255, 0.1);
            border: 1px solid rgba(0, 212, 255, 0.3);
            border-radius: 8px;
            padding: 15px;
            margin-bottom: 20px;
            font-family: monospace;
            font-size: 0.9rem;
        }
        .pipeline-info code { color: #00d4ff; }
        .input-section {
            background: rgba(255,255,255,0.05);
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 20px;
        }
        textarea {
            width: 100%;
            height: 150px;
            background: rgba(0,0,0,0.3);
            border: 1px solid rgba(255,255,255,0.1);
            border-radius: 8px;
            color: #fff;
            padding: 15px;
            font-size: 1rem;
            resize: vertical;
            margin-bottom: 15px;
        }
        textarea:focus { outline: none; border-color: #00d4ff; }
        button {
            background: linear-gradient(135deg, #00d4ff 0%, #0099cc 100%);
            color: #000;
            border: none;
            padding: 12px 30px;
            font-size: 1rem;
            font-weight: bold;
            border-radius: 8px;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        button:hover { transform: translateY(-2px); box-shadow: 0 5px 20px rgba(0,212,255,0.4); }
        button:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
        .results { display: none; }
        .results.visible { display: block; }
        .step-card {
            background: rgba(255,255,255,0.05);
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 15px;
            border-left: 4px solid #00d4ff;
        }
        .step-card.error { border-left-color: #ff4444; }
        .step-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
        }
        .step-name { font-weight: bold; color: #00d4ff; }
        .step-duration { color: #888; font-size: 0.85rem; }
        .step-output {
            background: rgba(0,0,0,0.3);
            border-radius: 8px;
            padding: 15px;
            font-family: monospace;
            font-size: 0.85rem;
            overflow-x: auto;
            white-space: pre-wrap;
            word-break: break-word;
        }
        .summary {
            background: rgba(0, 212, 255, 0.1);
            border: 1px solid rgba(0, 212, 255, 0.3);
            border-radius: 8px;
            padding: 15px;
            margin-top: 20px;
            text-align: center;
        }
        .loading {
            display: none;
            text-align: center;
            padding: 40px;
        }
        .loading.visible { display: block; }
        .spinner {
            width: 50px;
            height: 50px;
            border: 4px solid rgba(0,212,255,0.2);
            border-top-color: #00d4ff;
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin: 0 auto 20px;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .context-flow {
            display: flex;
            justify-content: center;
            gap: 10px;
            margin-bottom: 20px;
            flex-wrap: wrap;
        }
        .context-step {
            background: rgba(0,0,0,0.3);
            padding: 8px 15px;
            border-radius: 20px;
            font-size: 0.8rem;
        }
        .context-step.active { background: rgba(0,212,255,0.3); color: #00d4ff; }
        .arrow { color: #00d4ff; }
    </style>
</head>
<body>
    <div class="container">
        <h1>A2A Context Chaining Demo</h1>
        <p class="subtitle">Multi-Agent Pipeline with Accumulated Context</p>

        <div class="pipeline-info">
            <strong>Context Flow:</strong><br>
            Step 1: Sentiment → <code>{{original}}</code><br>
            Step 2: Analyzer → <code>{{original}} + {{sentiment}}</code><br>
            Step 3: Response → <code>{{original}} + {{sentiment}} + {{analysis}}</code><br>
            Step 4: Translation → <code>{{response}}</code>
        </div>

        <div class="input-section">
            <textarea id="complaint" placeholder="Enter customer complaint here...

Example: I've been waiting 3 weeks for my order #12345 and nobody responds to my emails! This is unacceptable. I want a full refund immediately or I'm disputing the charge with my bank."></textarea>
            <button id="submit" onclick="processComplaint()">Process Through Pipeline</button>
        </div>

        <div class="loading" id="loading">
            <div class="spinner"></div>
            <p>Processing through 4-agent pipeline...</p>
            <div class="context-flow" id="contextFlow">
                <span class="context-step" id="step1">Sentiment</span>
                <span class="arrow">→</span>
                <span class="context-step" id="step2">Analyzer</span>
                <span class="arrow">→</span>
                <span class="context-step" id="step3">Response</span>
                <span class="arrow">→</span>
                <span class="context-step" id="step4">Translation</span>
            </div>
        </div>

        <div class="results" id="results"></div>
    </div>

    <script>
        async function processComplaint() {
            const complaint = document.getElementById('complaint').value.trim();
            if (!complaint) {
                alert('Please enter a customer complaint');
                return;
            }

            const submitBtn = document.getElementById('submit');
            const loading = document.getElementById('loading');
            const results = document.getElementById('results');

            submitBtn.disabled = true;
            loading.classList.add('visible');
            results.classList.remove('visible');
            results.innerHTML = '';

            // Animate context flow
            let stepIndex = 0;
            const stepAnimation = setInterval(() => {
                document.querySelectorAll('.context-step').forEach(s => s.classList.remove('active'));
                if (stepIndex < 4) {
                    document.getElementById('step' + (stepIndex + 1)).classList.add('active');
                    stepIndex++;
                } else {
                    stepIndex = 0;
                }
            }, 800);

            try {
                const response = await fetch('/api/process', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ complaint })
                });

                const data = await response.json();
                clearInterval(stepAnimation);
                displayResults(data);
            } catch (error) {
                clearInterval(stepAnimation);
                results.innerHTML = `<div class="step-card error">
                    <div class="step-header"><span class="step-name">Error</span></div>
                    <div class="step-output">${error.message}</div>
                </div>`;
                results.classList.add('visible');
            } finally {
                submitBtn.disabled = false;
                loading.classList.remove('visible');
            }
        }

        function displayResults(data) {
            const results = document.getElementById('results');
            let html = '';

            if (data.steps) {
                data.steps.forEach((step, index) => {
                    const output = typeof step.output === 'object'
                        ? JSON.stringify(step.output, null, 2)
                        : step.output;

                    html += `<div class="step-card ${step.success ? '' : 'error'}">
                        <div class="step-header">
                            <span class="step-name">Step ${index + 1}: ${step.name}</span>
                            <span class="step-duration">${step.durationMs}ms</span>
                        </div>
                        <div class="step-output">${escapeHtml(output)}</div>
                    </div>`;
                });
            }

            if (data.totalDurationMs) {
                html += `<div class="summary">
                    <strong>Total Processing Time:</strong> ${data.totalDurationMs}ms |
                    <strong>Status:</strong> ${data.success ? 'All steps succeeded' : 'Some steps failed'}
                </div>`;
            }

            results.innerHTML = html;
            results.classList.add('visible');
        }

        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    </script>
</body>
</html>
""";
    }
}
