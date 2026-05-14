---
title: Apicurio Registry Support Chat
emoji: "\U0001F4AC"
colorFrom: blue
colorTo: purple
sdk: docker
app_port: 7860
---

# Apicurio Registry Support Chat

AI-powered support assistant for [Apicurio Registry](https://www.apicur.io/registry/)
with RAG-based documentation retrieval and Google Gemini LLM integration.

## Setup

1. Create a new Space on [Hugging Face](https://huggingface.co/new-space) with **Docker** SDK.
2. Add the `GOOGLE_AI_GEMINI_API_KEY` secret in **Settings > Secrets**.
3. Push this directory to your Space repository:

```bash
cd support-chat/huggingface
git init
git remote add space https://huggingface.co/spaces/<your-username>/apicurio-support-chat
git add .
git commit -m "Initial deployment"
git push space main
```

The Space will build and start automatically. The chat UI is available at the Space URL.

## Architecture

A single container runs two services via `supervisord`:

- **Apicurio Registry** (port 8080, internal) — stores prompt templates
- **Support Chat** (port 7860, exposed) — Quarkus app with LangChain4j + Gemini

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GOOGLE_AI_GEMINI_API_KEY` | Yes | Google AI API key for Gemini |
| `GEMINI_MODEL` | No | LLM model (default: `gemini-2.5-flash`) |
| `GEMINI_EMBEDDING_MODEL` | No | Embedding model (default: `gemini-embedding-001`) |
