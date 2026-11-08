# <Feature>: step-by-step guide

<!-- Template: see ../TEMPLATE.md. One idea per step; each step shows the API command and its
expected output, then the same thing in the UI with a screenshot when the UI shows it. -->

This guide walks through Apicurio Registry's <feature> with a running example. Follow it on your
own, or use it as the script for a live demo. Every step builds on the previous ones, so run them
in order.

**Time:** about <N> minutes. **You will see:**

- <first idea>;
- <second idea>.

For the reference documentation, see *<Docs title>* in the Registry documentation
([source](../../docs/modules/ROOT/pages/getting-started/assembly-<topic>.adoc)) and, for how it is
implemented, [ADR-NNNN](../../adr/NNNN-<title>.md).

## Before you start

You need:

- Docker with Docker Compose v2, or Podman with a Compose provider;
- `curl` and `jq`;
- a shell: bash or zsh;
- a browser, for the UI;
- free ports 8081 (Registry API) and 8888 (Registry UI). If they are in use, see
  [Using other ports](#using-other-ports).

Run every command from this directory:

```bash
cd examples/<feature>
```

## Step 1: Start the example

```bash
docker compose up -d
docker compose ps --format '{{.Name}}: {{.Status}}'
```

```
apicurio-registry-api-template: Up ...
apicurio-registry-ui-template: Up ...
```

## Step 2: Create the sample data and check everything works

```bash
./demo.sh
```

It ends with:

```
All checks passed.
```

The script is safe to run again. The data it creates:

| Group | Artifacts |
|-------|-----------|
| `<group>` | `<artifact>` |

## Step 3: Load the helper commands

```bash
source ./demo-env.sh
```

## Step 4: <One idea>

<Why it matters, in one or two sentences.>

```bash
status GET /groups/<group>/artifacts/<artifact>
```

```
200
```

**In the UI:** <what to click, and what you should see>.

![<What the screenshot shows>](images/01-<what-it-shows>.png)

## Step N: Clean up

```bash
docker compose down -v
```

## Presenter notes

- **Before the session:** run steps 1 to 3 in advance, so the images are downloaded and the data
  exists.
- **The story in one sentence per step:** <...>.
- **Questions that usually come up:**
  - *<Question>?* <Answer>.
- **To reset between runs:** `docker compose down -v`, then steps 1 to 3 again.

## Troubleshooting

| Symptom | What to do |
|---|---|
| `demo.sh` times out waiting for the Registry | Check `docker logs apicurio-registry-api-template`. Usually a port conflict: see [Using other ports](#using-other-ports). |
| Helper commands print nothing, or `jq: error` | Check that `curl` and `jq` are installed, and that you ran `source ./demo-env.sh` in the current shell. |

## Using other ports

If ports 8081 or 8888 are in use, choose others, and tell both Compose and the helpers:

```bash
export REGISTRY_PORT=18081 UI_PORT=18888
docker compose up -d
API=http://localhost:$REGISTRY_PORT/apis ./demo.sh
export API=http://localhost:$REGISTRY_PORT/apis
source ./demo-env.sh
```

The UI is then at `http://localhost:18888`.
