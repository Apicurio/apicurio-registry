# Storage write cost: after-step2

Commit: b3d6db1e30, date: 2026-11-27T17:34:08Z

### Idle baseline: 10 s with no requests (background jobs)

- wall time: 10000 ms (divisor 1: 10000 ms each)
- statements (all calls, incl. BEGIN/COMMIT/SAVEPOINT): 14 (per unit: 14.0)
- xact_commit delta: 4 (per unit: 4.0)

| calls | per unit | statement |
| ---: | ---: | --- |
| 7 | 7.00 | `BEGIN` |
| 7 | 7.00 | `SELECT r.type FROM global_rules r` |

### (a) Create artifact with first version, new group

- wall time: 259 ms (divisor 1: 259 ms each)
- statements (all calls, incl. BEGIN/COMMIT/SAVEPOINT): 59 (per unit: 59.0)
- xact_commit delta: 11 (per unit: 11.0)

| calls | per unit | statement |
| ---: | ---: | --- |
| 12 | 12.00 | `BEGIN` |
| 5 | 5.00 | `SELECT c.* FROM config c WHERE c.propName = $1` |
| 5 | 5.00 | `SELECT r.type FROM global_rules r` |
| 4 | 4.00 | `SELECT $3 FROM ONLY "public"."artifacts" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_` |
| 3 | 3.00 | `DELETE FROM outbox WHERE id = $1` |
| 3 | 3.00 | `INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload) VALUES ($1, $2, $3, $4, $5::jsonb)` |
| 2 | 2.00 | `INSERT INTO artifact_structured_content (groupId, artifactId, elementType, elementValue) VALUES ($1, $2, $3, $4)` |
| 2 | 2.00 | `SELECT COUNT(g.groupId) FROM groups g WHERE g.groupId = $1` |
| 2 | 2.00 | `SELECT nextval($1)` |
| 1 | 1.00 | `COMMIT` |
| 1 | 1.00 | `DELETE FROM artifact_structured_content WHERE groupId = $1 AND artifactId = $2` |
| 1 | 1.00 | `INSERT INTO artifacts (groupId, artifactId, type, owner, createdOn, modifiedBy, modifiedOn, name, description, labels) VALUES ($1, $2, $3, $` |
| 1 | 1.00 | `INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn) VALUES ($1, $2, $` |
| 1 | 1.00 | `INSERT INTO branch_versions (groupId, artifactId, branchId, branchOrder, version) SELECT $1, $2, $3, COALESCE(MAX(bv.branchOrder), $8) + $9,` |
| 1 | 1.00 | `INSERT INTO content (contentId, canonicalHash, contentHash, contentType, content, refs) VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT (content` |
| 1 | 1.00 | `INSERT INTO groups (groupId, description, artifactsType, owner, createdOn, modifiedBy, modifiedOn, labels) VALUES ($1, $2, $3, $4, $5, $6, $` |
| 1 | 1.00 | `INSERT INTO versions (globalId, groupId, artifactId, version, versionSortKey, versionOrder, state, name, description, owner, createdOn, modi` |
| 1 | 1.00 | `RELEASE SAVEPOINT JDBC_SAVEPOINT_0` |
| 1 | 1.00 | `ROLLBACK` |
| 1 | 1.00 | `SAVEPOINT JDBC_SAVEPOINT_0` |
| 1 | 1.00 | `SELECT $2 FROM ONLY "public"."content" x WHERE "contentid" OPERATOR(pg_catalog.=) $1 FOR KEY SHARE OF x` |
| 1 | 1.00 | `SELECT $4 FROM ONLY "public"."branches" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_c` |
| 1 | 1.00 | `SELECT $4 FROM ONLY "public"."versions" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_c` |
| 1 | 1.00 | `SELECT c.contentId FROM content c WHERE c.contentHash = $1` |
| 1 | 1.00 | `SELECT COUNT(a.artifactId) FROM artifacts a` |
| 1 | 1.00 | `SELECT COUNT(a.artifactId) FROM artifacts a WHERE a.groupId = $1 AND a.artifactId = $2` |
| 1 | 1.00 | `SELECT COUNT(v.globalId) FROM versions v` |
| 1 | 1.00 | `SELECT * FROM config c WHERE c.modifiedOn >= $1` |
| 1 | 1.00 | `SELECT r.* FROM group_rules r WHERE r.groupId = $1` |
| 1 | 1.00 | `SELECT v.*, a.type FROM versions v JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId WHERE v.globalId = $1` |

### (b) Create a new version of an existing artifact

- wall time: 132 ms (divisor 1: 132 ms each)
- statements (all calls, incl. BEGIN/COMMIT/SAVEPOINT): 59 (per unit: 59.0)
- xact_commit delta: 11 (per unit: 11.0)

| calls | per unit | statement |
| ---: | ---: | --- |
| 14 | 14.00 | `BEGIN` |
| 6 | 6.00 | `SELECT r.type FROM global_rules r` |
| 5 | 5.00 | `SELECT $3 FROM ONLY "public"."artifacts" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_` |
| 4 | 4.00 | `SELECT a.* FROM artifacts a WHERE a.groupId = $1 AND a.artifactId = $2` |
| 3 | 3.00 | `SELECT $2 FROM ONLY "public"."content" x WHERE "contentid" OPERATOR(pg_catalog.=) $1 FOR KEY SHARE OF x` |
| 2 | 2.00 | `INSERT INTO artifact_structured_content (groupId, artifactId, elementType, elementValue) VALUES ($1, $2, $3, $4)` |
| 2 | 2.00 | `SELECT COUNT(a.artifactId) FROM artifacts a WHERE a.groupId = $1 AND a.artifactId = $2` |
| 2 | 2.00 | `SELECT COUNT(v.globalId) FROM versions v WHERE v.groupId = $1 AND v.artifactId = $2` |
| 2 | 2.00 | `SELECT nextval($1)` |
| 1 | 1.00 | `DELETE FROM artifact_structured_content WHERE groupId = $1 AND artifactId = $2` |
| 1 | 1.00 | `DELETE FROM outbox WHERE id = $1` |
| 1 | 1.00 | `INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn) VALUES ($1, $2, $` |
| 1 | 1.00 | `INSERT INTO branch_versions (groupId, artifactId, branchId, branchOrder, version) SELECT $1, $2, $3, COALESCE(MAX(bv.branchOrder), $8) + $9,` |
| 1 | 1.00 | `INSERT INTO content (contentId, canonicalHash, contentHash, contentType, content, refs) VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT (content` |
| 1 | 1.00 | `INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload) VALUES ($1, $2, $3, $4, $5::jsonb)` |
| 1 | 1.00 | `INSERT INTO versions (globalId, groupId, artifactId, version, versionSortKey, versionOrder, state, name, description, owner, createdOn, modi` |
| 1 | 1.00 | `SELECT $4 FROM ONLY "public"."branches" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_c` |
| 1 | 1.00 | `SELECT $4 FROM ONLY "public"."versions" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_c` |
| 1 | 1.00 | `SELECT c.contentId FROM content c WHERE c.contentHash = $1` |
| 1 | 1.00 | `SELECT COUNT(g.groupId) FROM groups g WHERE g.groupId = $1` |
| 1 | 1.00 | `SELECT groupId, artifactId, version FROM versions WHERE globalId = $1` |
| 1 | 1.00 | `SELECT r.* FROM artifact_rules r WHERE r.groupId = $1 AND r.artifactId = $2` |
| 1 | 1.00 | `SELECT r.* FROM group_rules r WHERE r.groupId = $1` |
| 1 | 1.00 | `SELECT v.*, a.type FROM versions v JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId WHERE v.globalId = $1` |
| 1 | 1.00 | `SELECT v.contentId FROM versions v WHERE v.groupId = $1 AND v.artifactId = $2 AND v.state != $3 ORDER BY v.versionOrder` |
| 1 | 1.00 | `UPDATE artifacts SET modifiedOn = modifiedOn WHERE groupId = $1 AND artifactId = $2` |
| 1 | 1.00 | `UPDATE versions SET versionSortKey = $1 WHERE globalId = $2` |
| 1 | 1.00 | `UPDATE versions SET version = versionOrder WHERE globalId = $1` |

### (c) 100 sequential artifact creates, existing group (per create)

- wall time: 5361 ms (divisor 100: 53 ms each)
- statements (all calls, incl. BEGIN/COMMIT/SAVEPOINT): 3220 (per unit: 32.2)
- xact_commit delta: 408 (per unit: 4.0)

| calls | per unit | statement |
| ---: | ---: | --- |
| 410 | 4.10 | `BEGIN` |
| 400 | 4.00 | `SELECT 1 FROM ONLY "public"."artifacts" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_c` |
| 200 | 2.00 | `DELETE FROM outbox WHERE id = $1` |
| 200 | 2.00 | `INSERT INTO artifact_structured_content (groupId, artifactId, elementType, elementValue) VALUES ($1, $2, $3, $4)` |
| 200 | 2.00 | `INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload) VALUES ($1, $2, $3, $4, $5::jsonb)` |
| 200 | 2.00 | `SELECT COUNT(g.groupId) FROM groups g WHERE g.groupId = $1` |
| 200 | 2.00 | `SELECT nextval($1)` |
| 106 | 1.06 | `SELECT r.type FROM global_rules r` |
| 100 | 1.00 | `DELETE FROM artifact_structured_content WHERE groupId = $1 AND artifactId = $2` |
| 100 | 1.00 | `INSERT INTO artifacts (groupId, artifactId, type, owner, createdOn, modifiedBy, modifiedOn, name, description, labels) VALUES ($1, $2, $3, $` |
| 100 | 1.00 | `INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn) VALUES ($1, $2, $` |
| 100 | 1.00 | `INSERT INTO branch_versions (groupId, artifactId, branchId, branchOrder, version) SELECT $1, $2, $3, COALESCE(MAX(bv.branchOrder), $8) + $9,` |
| 100 | 1.00 | `INSERT INTO content (contentId, canonicalHash, contentHash, contentType, content, refs) VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT (content` |
| 100 | 1.00 | `INSERT INTO versions (globalId, groupId, artifactId, version, versionSortKey, versionOrder, state, name, description, owner, createdOn, modi` |
| 100 | 1.00 | `SELECT 1 FROM ONLY "public"."branches" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_ca` |
| 100 | 1.00 | `SELECT 1 FROM ONLY "public"."content" x WHERE "contentid" OPERATOR(pg_catalog.=) $1 FOR KEY SHARE OF x` |
| 100 | 1.00 | `SELECT 1 FROM ONLY "public"."versions" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_ca` |
| 100 | 1.00 | `SELECT c.contentId FROM content c WHERE c.contentHash = $1` |
| 100 | 1.00 | `SELECT COUNT(a.artifactId) FROM artifacts a WHERE a.groupId = $1 AND a.artifactId = $2` |
| 100 | 1.00 | `SELECT r.* FROM group_rules r WHERE r.groupId = $1` |
| 100 | 1.00 | `SELECT v.*, a.type FROM versions v JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId WHERE v.globalId = $1` |
| 1 | 0.01 | `DELETE FROM downloads WHERE expires < $1` |
| 1 | 0.01 | `SELECT COUNT(a.artifactId) FROM artifacts a` |
| 1 | 0.01 | `SELECT COUNT(v.globalId) FROM versions v` |
| 1 | 0.01 | `SELECT * FROM config c WHERE c.modifiedOn >= $1` |

### (d) Async MCP import of 500 tools (to COMPLETED; per tool)

- wall time: 7459 ms (divisor 500: 14 ms each)
- statements (all calls, incl. BEGIN/COMMIT/SAVEPOINT): 19679 (per unit: 39.3)
- xact_commit delta: 3071 (per unit: 6.1)

| calls | per unit | statement |
| ---: | ---: | --- |
| 3055 | 6.11 | `BEGIN` |
| 2013 | 4.03 | `SELECT $3 FROM ONLY "public"."artifacts" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_` |
| 1003 | 2.01 | `DELETE FROM outbox WHERE id = $1` |
| 1003 | 2.01 | `INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload) VALUES ($1, $2, $3, $4, $5::jsonb)` |
| 1003 | 2.01 | `SELECT 1 FROM ONLY "public"."content" x WHERE "contentid" OPERATOR(pg_catalog.=) $1 FOR KEY SHARE OF x` |
| 1002 | 2.00 | `SELECT COUNT(a.artifactId) FROM artifacts a WHERE a.groupId = $1 AND a.artifactId = $2` |
| 1002 | 2.00 | `SELECT nextval('contentid_seq')` |
| 1001 | 2.00 | `SELECT COUNT(g.groupId) FROM groups g WHERE g.groupId = $1` |
| 1000 | 2.00 | `INSERT INTO artifact_structured_content (groupId, artifactId, elementType, elementValue) VALUES ($1, $2, $3, $4)` |
| 507 | 1.01 | `SELECT r.type FROM global_rules r` |
| 501 | 1.00 | `DELETE FROM artifact_structured_content WHERE groupId = $1 AND artifactId = $2` |
| 501 | 1.00 | `INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn) VALUES ($1, $2, $` |
| 501 | 1.00 | `INSERT INTO branch_versions (groupId, artifactId, branchId, branchOrder, version) SELECT $1, $2, $3, COALESCE(MAX(bv.branchOrder), 0) + 1, $` |
| 501 | 1.00 | `INSERT INTO content (contentId, canonicalHash, contentHash, contentType, content, refs) VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT (content` |
| 501 | 1.00 | `SELECT 1 FROM ONLY "public"."branches" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_ca` |
| 501 | 1.00 | `SELECT 1 FROM ONLY "public"."versions" x WHERE "groupid"::pg_catalog.text OPERATOR(pg_catalog.=) $1::pg_catalog.text AND "artifactid"::pg_ca` |
| 501 | 1.00 | `SELECT c.contentId FROM content c WHERE c.contentHash = $1` |
| 501 | 1.00 | `SELECT r.* FROM group_rules r WHERE r.groupId = $1` |
| 501 | 1.00 | `SELECT v.*, a.type FROM versions v JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId WHERE v.globalId = $1` |
| 500 | 1.00 | `INSERT INTO artifacts (groupId, artifactId, type, owner, createdOn, modifiedBy, modifiedOn, name, description, labels) VALUES ($1, $2, $3, $` |
| 500 | 1.00 | `INSERT INTO content_references (contentId, groupId, artifactId, version, name) VALUES ($1, $2, $3, $4, $5)` |
| 500 | 1.00 | `INSERT INTO versions (globalId, groupId, artifactId, version, versionSortKey, versionOrder, state, name, description, owner, createdOn, modi` |
| 500 | 1.00 | `SELECT c.content, c.contentType, c.refs, c.contentHash FROM content c WHERE c.contentId = $1` |
| 500 | 1.00 | `SELECT v.*, a.type FROM versions v JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId WHERE v.groupId = $1 AND v.arti` |
| 40 | 0.08 | `SELECT a.* FROM artifacts a WHERE a.groupId = $1 AND a.artifactId = $2` |
| 10 | 0.02 | `INSERT INTO artifact_labels (groupId, artifactId, labelKey, labelValue) VALUES ($1, $2, $3, $4)` |
| 5 | 0.01 | `SELECT c.contentId, c.content, c.contentType, c.refs, c.contentHash FROM content c WHERE c.contentId IN ($1, $2, $3, $4, $5, $6, $7, $8, $9,` |
| 5 | 0.01 | `SELECT v.*, a.type FROM versions v JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId WHERE (v.groupId = $1 AND v.art` |
| 3 | 0.01 | `UPDATE artifacts SET modifiedOn = modifiedOn WHERE groupId = $1 AND artifactId = $2` |
| 2 | 0.00 | `DELETE FROM artifact_labels WHERE groupId = $1 AND artifactId = $2` |
| 2 | 0.00 | `SELECT COUNT(v.globalId) FROM versions v WHERE v.groupId = $1 AND v.artifactId = $2` |
| 2 | 0.00 | `UPDATE artifacts SET labels = $1 WHERE groupId = $2 AND artifactId = $3` |
| 2 | 0.00 | `UPDATE artifacts SET modifiedBy = $1, modifiedOn = $2 WHERE groupId = $3 AND artifactId = $4` |
| 1 | 0.00 | `INSERT INTO versions (globalId, groupId, artifactId, version, versionSortKey, versionOrder, state, name, description, owner, createdOn, modi` |
| 1 | 0.00 | `SELECT bv.groupId, bv.artifactId, bv.version FROM branch_versions bv WHERE bv.groupId = $1 AND bv.artifactId = $2 AND bv.branchId = $3 ORDER` |
| 1 | 0.00 | `SELECT COUNT(a.artifactId) FROM artifacts a` |
| 1 | 0.00 | `SELECT COUNT(v.globalId) FROM versions v` |
| 1 | 0.00 | `SELECT groupId, artifactId, version FROM versions WHERE globalId = $1` |
| 1 | 0.00 | `SELECT r.* FROM artifact_rules r WHERE r.groupId = $1 AND r.artifactId = $2` |
| 1 | 0.00 | `SELECT v.contentId FROM versions v WHERE v.groupId = $1 AND v.artifactId = $2 AND v.state != $3 ORDER BY v.versionOrder` |
| 1 | 0.00 | `SELECT v.globalId, v.version, v.versionOrder, c.contentId, c.content, c.contentType, c.refs FROM versions v JOIN content c ON v.contentId = ` |
| 1 | 0.00 | `UPDATE versions SET versionSortKey = $1 WHERE globalId = $2` |
| 1 | 0.00 | `UPDATE versions SET version = versionOrder WHERE globalId = $1` |

