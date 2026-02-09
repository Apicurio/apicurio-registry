#!/usr/bin/env python3
"""
Code Review Agent - LLM-powered A2A Agent

This agent implements the A2A (Agent-to-Agent) protocol and supports multiple
LLM providers (Claude, Ollama) for AI-powered code review. It exposes:
- /.well-known/agent.json - Agent card (A2A discovery)
- /agents/code-review/invoke - Task execution endpoint
"""

import os
import json
import requests
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# Configuration from environment
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "ollama")  # "claude" or "ollama"
ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY", "")
CLAUDE_MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-4-20250514")
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2")
AGENT_PORT = int(os.getenv("AGENT_PORT", "8080"))
REGISTRY_URL = os.getenv("REGISTRY_URL", "http://localhost:8080")

# Initialize Anthropic client if using Claude
anthropic_client = None
if LLM_PROVIDER == "claude" and ANTHROPIC_API_KEY:
    try:
        import anthropic
        anthropic_client = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)
        print(f"[code-review-agent] Claude API initialized with model: {CLAUDE_MODEL}")
    except ImportError:
        print("[code-review-agent] WARNING: anthropic package not installed, falling back to Ollama")
        LLM_PROVIDER = "ollama"
    except Exception as e:
        print(f"[code-review-agent] WARNING: Failed to initialize Claude: {e}, falling back to Ollama")
        LLM_PROVIDER = "ollama"

# Agent Card - A2A Protocol
AGENT_CARD = {
    "$schema": "https://apicur.io/schemas/agent-card/v1",
    "name": "code-review-agent",
    "description": "AI-powered code review agent using local LLM (Ollama). Analyzes code quality, identifies bugs, and suggests improvements.",
    "version": "1.0.0",
    "url": f"http://localhost:8081",
    "provider": {
        "organization": "Apicurio Demo",
        "url": "https://www.apicur.io"
    },
    "capabilities": {
        "streaming": False,
        "pushNotifications": False
    },
    "skills": [
        {
            "id": "code-analysis",
            "name": "Code Analysis",
            "description": "Analyzes code for quality, patterns, and anti-patterns",
            "tags": ["code", "analysis", "quality"]
        },
        {
            "id": "bug-detection",
            "name": "Bug Detection",
            "description": "Identifies potential bugs and issues in code",
            "tags": ["bugs", "issues", "detection"]
        },
        {
            "id": "suggestion-generation",
            "name": "Suggestion Generation",
            "description": "Generates improvement suggestions for code",
            "tags": ["suggestions", "improvements"]
        }
    ],
    "authentication": {
        "schemes": ["none"]
    },
    "defaultInputModes": ["text"],
    "defaultOutputModes": ["text", "json"]
}

# System prompt for code review
SYSTEM_PROMPT = """You are an expert code reviewer. Analyze the provided code and return a JSON response with:

{
  "summary": "Brief overall assessment",
  "score": <0-100 quality score>,
  "issues": [
    {
      "severity": "critical|major|minor|info",
      "line": <line number or null>,
      "description": "Issue description",
      "suggestion": "How to fix it"
    }
  ],
  "strengths": ["List of good practices found"],
  "recommendations": ["General improvement suggestions"]
}

Be thorough but concise. Focus on:
- Security vulnerabilities (SQL injection, XSS, etc.)
- Potential bugs and edge cases
- Performance issues
- Code style and readability
- Best practices

IMPORTANT: Return ONLY valid JSON, no markdown, no explanations outside the JSON."""


def call_claude(prompt: str, system_prompt: str = None) -> str:
    """Call Claude API for LLM inference."""
    if not anthropic_client:
        return json.dumps({
            "error": "Claude client not initialized",
            "summary": "Failed to analyze code",
            "score": 0,
            "issues": []
        })

    try:
        messages = [{"role": "user", "content": prompt}]
        kwargs = {
            "model": CLAUDE_MODEL,
            "max_tokens": 4096,
            "messages": messages
        }
        if system_prompt:
            kwargs["system"] = system_prompt

        response = anthropic_client.messages.create(**kwargs)
        return response.content[0].text

    except Exception as e:
        return json.dumps({
            "error": f"Claude API error: {str(e)}",
            "summary": "Failed to analyze code",
            "score": 0,
            "issues": []
        })


def call_ollama(prompt: str, system_prompt: str = None) -> str:
    """Call Ollama API for LLM inference."""
    try:
        payload = {
            "model": OLLAMA_MODEL,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.3,
                "num_ctx": 4096
            }
        }

        if system_prompt:
            payload["system"] = system_prompt

        response = requests.post(
            f"{OLLAMA_URL}/api/generate",
            json=payload,
            timeout=120
        )
        response.raise_for_status()

        result = response.json()
        return result.get("response", "")

    except requests.exceptions.RequestException as e:
        return json.dumps({
            "error": f"Ollama API error: {str(e)}",
            "summary": "Failed to analyze code",
            "score": 0,
            "issues": []
        })


def call_llm(prompt: str, system_prompt: str = None) -> str:
    """Call the configured LLM provider."""
    if LLM_PROVIDER == "claude" and anthropic_client:
        return call_claude(prompt, system_prompt)
    else:
        return call_ollama(prompt, system_prompt)


def parse_llm_response(response: str) -> dict:
    """Parse LLM response, handling potential JSON extraction."""
    # Try direct JSON parse
    try:
        return json.loads(response)
    except json.JSONDecodeError:
        pass

    # Try to extract JSON from markdown code block
    if "```json" in response:
        try:
            start = response.index("```json") + 7
            end = response.index("```", start)
            return json.loads(response[start:end].strip())
        except (ValueError, json.JSONDecodeError):
            pass

    # Try to find JSON object in response
    try:
        start = response.index("{")
        end = response.rindex("}") + 1
        return json.loads(response[start:end])
    except (ValueError, json.JSONDecodeError):
        pass

    # Return error response
    return {
        "summary": "Failed to parse LLM response",
        "score": 0,
        "issues": [],
        "raw_response": response[:500]
    }


# A2A Protocol Endpoints

@app.route("/.well-known/agent.json", methods=["GET"])
def get_agent_card():
    """Return the agent card for A2A discovery."""
    return jsonify(AGENT_CARD)


@app.route("/agents/code-review", methods=["GET"])
def get_agent_info():
    """Return agent information."""
    return jsonify(AGENT_CARD)


@app.route("/agents/code-review/invoke", methods=["POST"])
def invoke_agent():
    """
    A2A Task Execution Endpoint

    Accepts a code review request and returns AI-powered analysis.

    Request body:
    {
        "code": "the code to review",
        "language": "programming language",
        "filename": "optional filename",
        "focus_areas": "optional specific focus"
    }

    Or for A2A protocol:
    {
        "message": "the code or prompt to analyze"
    }
    """
    try:
        data = request.get_json()

        if not data:
            return jsonify({"error": "No JSON body provided"}), 400

        # Handle different input formats
        if "code" in data:
            code = data["code"]
            language = data.get("language", "unknown")
            filename = data.get("filename", "code.txt")
            focus = data.get("focus_areas", "")
        elif "message" in data:
            # A2A protocol format
            code = data["message"]
            language = "unknown"
            filename = "code.txt"
            focus = ""
        elif "prompt" in data:
            # Alternative format
            code = data["prompt"]
            language = data.get("language", "unknown")
            filename = data.get("filename", "code.txt")
            focus = ""
        else:
            return jsonify({"error": "Missing 'code', 'message', or 'prompt' field"}), 400

        # Build the prompt
        prompt = f"""Please review the following {language} code from file '{filename}':

```{language}
{code}
```

{f"Focus areas: {focus}" if focus else "Perform a comprehensive review."}

Provide your analysis as JSON."""

        print(f"[code-review-agent] Analyzing code ({len(code)} chars) using {LLM_PROVIDER}...")

        # Call the configured LLM provider
        llm_response = call_llm(prompt, SYSTEM_PROMPT)

        # Parse the response
        result = parse_llm_response(llm_response)

        print(f"[code-review-agent] Analysis complete. Score: {result.get('score', 'N/A')}")

        # Add metadata
        result["agent"] = "code-review-agent"
        result["provider"] = LLM_PROVIDER
        result["model"] = CLAUDE_MODEL if LLM_PROVIDER == "claude" else OLLAMA_MODEL

        return jsonify(result)

    except Exception as e:
        print(f"[code-review-agent] Error: {str(e)}")
        return jsonify({
            "error": str(e),
            "summary": "Internal error during code review",
            "score": 0,
            "issues": []
        }), 500


@app.route("/health", methods=["GET"])
def health_check():
    """Health check endpoint."""
    # Check Ollama connectivity
    try:
        response = requests.get(f"{OLLAMA_URL}/api/tags", timeout=5)
        ollama_status = "healthy" if response.ok else "unhealthy"
    except Exception:
        ollama_status = "unreachable"

    return jsonify({
        "status": "healthy",
        "agent": "code-review-agent",
        "ollama": ollama_status,
        "model": OLLAMA_MODEL
    })


@app.route("/", methods=["GET"])
def root():
    """Root endpoint with API info."""
    return jsonify({
        "name": "Code Review Agent",
        "version": "1.0.0",
        "endpoints": {
            "agent_card": "/.well-known/agent.json",
            "invoke": "/agents/code-review/invoke",
            "health": "/health"
        },
        "documentation": "POST to /agents/code-review/invoke with {\"code\": \"...\", \"language\": \"...\"}"
    })


if __name__ == "__main__":
    print(f"Starting Code Review Agent on port {AGENT_PORT}")
    print(f"Ollama URL: {OLLAMA_URL}")
    print(f"Ollama Model: {OLLAMA_MODEL}")
    print(f"Registry URL: {REGISTRY_URL}")
    app.run(host="0.0.0.0", port=AGENT_PORT, debug=False)
