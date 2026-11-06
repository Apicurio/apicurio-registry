# Writing an example

Every example shows one feature set end to end, and checks itself. The reference is
[`per-resource-authorization`](per-resource-authorization/). Start from the skeleton in
[`_template/`](_template/):

```bash
cp -r examples/_template examples/<feature>
```

Then replace every `<placeholder>`, rename the `*-template` container names to `*-<feature>`, and
delete the template comments. The skeleton runs as-is (`docker compose up -d && ./demo.sh`), so you
can check it works before changing anything.

## Required files

| File | Purpose |
|---|---|
| `README.md` | Summary, quick start, services, files, notes, learn more |
| `GUIDE.md` | Step-by-step walkthrough with API commands, expected output and UI screenshots |
| `docker-compose.yml` | Everything the example needs; nothing to install apart from Docker, `curl` and `jq` |
| `demo.sh` | Seeds the data, then checks the expected behaviour; exits non-zero on any failure |
| `demo-env.sh` | Optional: shell helpers to call the API by hand (`source ./demo-env.sh`) |
| `images/` | Optional: screenshots, named `NN-what-it-shows.png` in step order |

Example-specific files (rules, schemas, configuration) live next to these, with a comment on each
non-obvious line.

## Rules

- **Two commands to run it:** `docker compose up -d` then `./demo.sh`. Success is the literal line
  `All checks passed.`
- **`demo.sh` checks behaviour, not just reachability.** Assert exact status codes and exact values,
  including the failure cases (409 on a rule violation, 403 on a denial). Each check prints one
  `ok`/`FAIL` line. It must be safe to run twice. CI runs it, so it must not need a browser or input.
- **One idea per guide step.** Show the API command, its expected output, then the same thing in the
  UI when the UI shows it. End with Clean up, Presenter notes, Troubleshooting and Using other ports.
- **Ports are overridable** through environment variables (`REGISTRY_PORT`, `UI_PORT`, ...), and the
  scripts read `API` and the other URLs from the environment.
- **Compose is commented**: every non-default setting says why it is there. Use health checks and
  `depends_on: condition: service_healthy` instead of sleeps. Reuse shared files (for example
  `distro/docker-compose/config/realm.json`) rather than copying them.
- **Use `latest-snapshot` images** and say in the README how to run a local build.
- **Say what is not production-ready** (dev-mode Keycloak, relaxed issuer checks, in-memory storage).
- **Link both ways:** the README links the user documentation and the ADR; the ADR links the example.
- **Replaces older examples?** Delete them in the same PR, and update [`README.md`](README.md).
