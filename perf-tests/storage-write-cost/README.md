# Storage write cost (#11412)

`measure.sh` counts the SQL statements and transactions that the registry runs against PostgreSQL
for common write requests. It starts PostgreSQL 16 (with `pg_stat_statements`) in a container, the
packaged registry (`app/target/quarkus-app/quarkus-run.jar`) against it, and the MCP tools stub from
this module, then runs four scenarios:

- (a) one artifact create with a first version (AVRO), in a new group, via REST v3;
- (b) one new version of that artifact;
- (c) 100 sequential artifact creates in the same (existing) group, reported per create;
- (d) an async MCP import of 500 tools, timed from the `mcp-import` request until the status
  endpoint reports `COMPLETED`, reported per tool.

```bash
./mvnw -q -B -ntp package -pl app -DskipTests -Dcheckstyle.skip
LABEL=before perf-tests/storage-write-cost/measure.sh   # writes perf-tests/target/storage-write-cost/before.md
```

Variables: `CONTAINER_CLI` (default `podman`), `CREATES` (100), `TOOLS` (500), `LABEL`, `OUT_DIR`.

## How to read the numbers

- Statistics are read as a separate role connected to another database, so the metering queries
  are not counted. Everything else on the `registry` database is, including the registry's
  background jobs: see the idle baseline in each result file (a few statements and transactions per
  10 s). Scenarios (a) and (b) are single requests inside a ~22 s window, so their totals include
  that noise; (c) and (d) average it away and are the reliable numbers.
- `BEGIN` calls count the transactions the registry opened (pgjdbc sends `BEGIN` because auto-commit
  is off). `xact_commit` is the database's commit counter; it is flushed lazily, so the script waits
  11 s before reading it.
- `SELECT 1 FROM ONLY "public"."artifacts" x ... FOR KEY SHARE` rows are PostgreSQL's own foreign-key
  checks, not statements sent by the registry.
- The outbox `INSERT` + `DELETE` pairs are the Debezium outbox pattern and are expected.
- Scenario (d) also counts the status polls made while waiting.

## Results before (origin/main e4b9b3d830)

Full tables: [results-before.md](results-before.md).

| Scenario | Statements | Transactions (`xact_commit`) | Wall time |
| --- | ---: | ---: | ---: |
| (a) create, new group (one request, incl. noise) | 63 | 17 | 216 ms |
| (b) new version (one request, incl. noise) | 64 | 17 | 132 ms |
| (c) create, existing group, per create | 38.2 | 8.0 | 59 ms |
| (d) MCP import, per tool (500 tools) | 45.4 | 10.1 | 20 ms (10.4 s total) |

### Redundant work per request, ranked

From scenario (c), per artifact create (8 transactions, 38 statements):

1. **Separate transactions that can be one.** The storage create runs as four transactions:
   group existence check, content insert (lookup + `nextval` + insert), content id re-read by hash,
   and the artifact/version insert. All four can be one transaction (saves 3 transactions, 6 `BEGIN`/
   `COMMIT` round trips). The REST layer adds four more, one per read (artifact exists, group exists,
   group rules, global rules), which are independent reads and stay separate without a unit of work.
2. **Group existence read three times** (`SELECT COUNT(g.groupId) FROM groups`, 3 per create): the
   rules service checks the group exists before reading its rules, `getGroupRules` checks it again
   when the group has no rules, and the storage create checks it a third time. The first check is
   redundant with the second (saves 1 statement and 1 transaction).
3. **Content id read twice** (`SELECT c.contentId FROM content c WHERE c.contentHash = ?`, 2 per
   create): the dedupe lookup runs before the insert, then the id is read again in a new transaction
   although the insert path already knows it (saves 1 statement and 1 transaction).
4. Kept, not redundant: `nextval('contentid_seq')` is allocated even for duplicate content, on
   purpose (unchanged id allocation); the version metadata read after the insert (`SELECT v.*, a.type
   ... WHERE v.globalId = ?`) builds the response and the outbox event inside the create transaction;
   the structured-content `DELETE` + `INSERT`s index the new content.
5. New version (b): when no version is given, the version row is inserted and then updated twice
   (`UPDATE versions SET version = versionOrder`, `UPDATE versions SET versionSortKey`). Folding them
   into the insert would need the order before the insert; left for later.

The MCP import (d) is dominated by the same per-artifact create path (about 10 transactions per tool);
its callers adopt the unit of work after the agents module split (#11420).
