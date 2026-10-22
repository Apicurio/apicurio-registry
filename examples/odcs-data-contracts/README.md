# ODCS Data Contracts — Live Demo

Interactive demo of the Apicurio Registry Data Contracts framework (Phases 1-8). Designed for live team presentations — the demo pauses between steps so you can narrate with the slides.

## Setup

### Option A: Docker Compose (recommended)

```bash
cd examples/odcs-data-contracts
docker compose up -d
```

This starts:
- **Registry API** at `http://localhost:8080/apis/registry/v3`
- **Registry UI** at `http://localhost:8888`

### Option B: Build from source

```bash
# Build
./mvnw clean install -DskipTests

# Start registry
cd app/ && ../mvnw quarkus:dev

# Start UI (separate terminal)
cd ui && npm install && cd ui-app && npm run dev
# UI at http://localhost:8888
```

### Run the demo

Open `docs/presentations/odcs-data-contracts-presentation.html` in your browser. Arrow keys to navigate, `S` for speaker notes.

In a separate terminal:

```bash
cd examples/odcs-data-contracts
mvn compile exec:java
```

The demo pauses before each step — press **Enter** to continue. Each `▶ DEMO` slide tells you when to switch to the terminal.

For automated (non-interactive) runs:

```bash
mvn compile exec:java -Dexec.args="--no-pause"
```

### UI tour (slide 12)

When you reach the UI Tour slide, open the registry UI and navigate to **Explore → odcs-example → OrderEvent → Contract tab**.

## Demo Flow

| Step | Slide | Phase | What happens |
|------|-------|-------|-------------|
| 1 | 3 | 1-3 | Register Avro schema with inline PII tags |
| 2 | 4 | 1-3 | Submit ODCS contract → projection (rules, labels, tags) |
| 3 | 5 | 1-3 | List contracts, get metadata, export as ODCS YAML |
| 4-5 | 6 | 4 | Quality score + promote DEV → STAGE |
| 6-7 | 7 | 5 | Verify projected CEL rules + execute (pass and fail) |
| 8 | 8 | 6 | Compatibility group + schema v2 + JSONata migration |
| 9 | 9 | 7 | Search contracts + audit log |
| 10 | 10 | 8 | Set global contract rules (org-wide CEL) |
| 11-14 | 11 | SerDes | Kafka: start → produce valid → produce invalid (rejected) → consume |
| — | 12 | UI | Walk through Contract tab in browser |

## Presentation Slides

| # | Title | Type |
|---|-------|------|
| 1 | Title + Tech Stack | Intro |
| 2 | Projection Model | Concept |
| 3 | Register Schema | ▶ DEMO |
| 4 | Submit ODCS Contract | ▶ DEMO |
| 5 | Inspect Contract | ▶ DEMO |
| 6 | Quality & Governance | ▶ DEMO |
| 7 | CEL Rule Engine | ▶ DEMO |
| 8 | Schema Migration | ▶ DEMO |
| 9 | Search & Audit | ▶ DEMO |
| 10 | Global Rules | ▶ DEMO |
| 11 | Kafka SerDes | ▶ DEMO |
| 12 | UI Tour | Live browser |
| 13 | Architecture | Concept |
| 14 | Storage Deep Dive | Technical |
| 15 | What's Next + Q&A | Discussion |
| 16 | Summary | Wrap-up |
