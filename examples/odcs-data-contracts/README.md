# ODCS Data Contracts Demo

End-to-end demonstration of the Apicurio Registry Data Contracts framework (Phases 1-8).

## Prerequisites

- Java 21+
- Maven 3.9+

## Quick Start

### 1. Build the registry

From the repository root:

```bash
./mvnw clean install -DskipTests
```

### 2. Start the registry

```bash
cd app/
../mvnw quarkus:dev
```

The registry starts on `http://localhost:8080`. The API is available at `http://localhost:8080/apis/registry/v3`.

### 3. Run the demo

In a separate terminal:

```bash
cd examples/odcs-data-contracts
mvn compile exec:java
```

### 4. (Optional) Start the UI

In a third terminal:

```bash
cd ui
npm install
cd ui-app
./init-dev.sh
npm run dev
```

Open `http://localhost:8888` in your browser. Navigate to **Explore > odcs-example > OrderEvent > Contract** tab to see contract metadata, quality scores, and audit log.

## What the Demo Does

| Step | Phase | What it does |
|------|-------|-------------|
| 1 | 1 | Registers an Avro schema (`OrderEvent`) with PII-tagged fields |
| 2 | 3 | Submits an ODCS v3.1 contract referencing the schema, projects labels/rules/tags |
| 3 | 3 | Lists all contracts in the group |
| 4 | 1 | Retrieves contract metadata (status, owner, classification) |
| 5 | 3 | Exports the artifact state back as ODCS YAML |
| 6 | 4 | Checks quality score (completeness, compliance, stability) |
| 7 | 4 | Promotes contract through stages: DEV -> STAGE |
| 8 | 5 | Sets contract rules: CEL domain rule + JSONata migration rule |
| 9 | 5 | Executes CEL rules against a test record |
| 10 | 6 | Sets a compatibility group for schema evolution scoping |
| 11 | 6 | Registers v2 schema (adds `currency` field), migrates a record v1 -> v2 using JSONata |
| 12 | 7 | Searches for contracts across artifacts |
| 13 | 7 | Retrieves the audit log showing all operations performed |
| 14 | 8 | Sets global contract rules that apply to all artifacts |
| 15 | — | Cleans up: deletes global rules, contract, and schema |

## UI Features

After running the demo (without the cleanup step), open the UI and navigate to:

**Explore > odcs-example > OrderEvent > Contract tab**

You will see:
- **Contract Metadata** — status (STABLE), owner team (orders-team), classification (CONFIDENTIAL), promotion stage (STAGE), compatibility group (orders-v1)
- **Quality Score** — gauges for overall, completeness, compliance, and stability scores
- **Audit Log** — chronological list of all contract operations with timestamps and principals

## Presentation

Open `docs/presentations/odcs-data-contracts-presentation.html` in any browser to view the full 18-slide presentation covering the architecture, design decisions, and all 8 phases.
