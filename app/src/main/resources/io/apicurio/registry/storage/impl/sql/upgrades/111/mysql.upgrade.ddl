-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade Script from 110 to 111
--
-- Retry-safe: MySQL DDL auto-commits each statement, so a failure mid-
-- script leaves the database partially upgraded.  All constraints and
-- indexes are inlined into CREATE TABLE IF NOT EXISTS so that the entire
-- table definition (including PK, FK, unique, check, and indexes) is
-- created atomically in one statement.  A retry sees the existing table
-- and skips it; no separate ALTER TABLE or CREATE INDEX can fail with
-- "already exists".  The version marker is written last so a partial
-- failure leaves the version at 110 and the full upgrade is retried on
-- the next application start.
--
-- CHECK constraint enforcement: enforced only on MySQL >= 8.0.16.
-- Earlier 8.0 releases parse CHECK but silently ignore it.  If your
-- deployment uses MySQL < 8.0.16, the attemptCount >= 0 invariant must
-- be maintained by the application layer.
-- *********************************************************************

-- serves: SELECT ... WHERE enabled = TRUE AND deletedOn IS NULL (recorder hot path)
CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    subscriptionId     VARCHAR(36)   NOT NULL,
    name               VARCHAR(512)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    ownerId            VARCHAR(256)  NOT NULL,
    endpointUrl        VARCHAR(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    eventTypes         TEXT          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    groupFilter        VARCHAR(512),
    artifactIdFilter   VARCHAR(512),
    artifactTypeFilter VARCHAR(32),
    enabled            BOOLEAN       NOT NULL,
    deletedOn          BIGINT,
    revision           BIGINT        NOT NULL,
    signingSecretRef   VARCHAR(512),
    createdOn          BIGINT        NOT NULL,
    modifiedOn         BIGINT        NOT NULL,
    PRIMARY KEY (subscriptionId),
    INDEX IDX_whsubs_1 (enabled, deletedOn),
    INDEX IDX_whsubs_2 (createdOn)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;

CREATE TABLE IF NOT EXISTS webhook_events (
    eventRowId   VARCHAR(36)  NOT NULL,
    source       TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    eventId      TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    identityHash VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    eventType    VARCHAR(256) NOT NULL,
    payload      LONGBLOB     NOT NULL,
    createdOn    BIGINT       NOT NULL,
    PRIMARY KEY (eventRowId),
    CONSTRAINT UQ_whevents_1 UNIQUE (identityHash),
    INDEX IDX_whevents_1 (createdOn)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;

CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
    deliveryId     VARCHAR(36) NOT NULL,
    subscriptionId VARCHAR(36) NOT NULL,
    eventRowId     VARCHAR(36) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    attemptCount   INT         NOT NULL DEFAULT 0,
    nextAttemptAt  BIGINT,
    claimToken     VARCHAR(36),
    leaseUntil     BIGINT,
    lastAttemptAt  BIGINT,
    httpStatusCode INT,
    errorCode      VARCHAR(64),
    createdOn      BIGINT      NOT NULL,
    updatedOn      BIGINT      NOT NULL,
    completedOn    BIGINT,
    PRIMARY KEY (deliveryId),
    CONSTRAINT FK_whdlogs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId),
    CONSTRAINT FK_whdlogs_2 FOREIGN KEY (eventRowId) REFERENCES webhook_events(eventRowId),
    CONSTRAINT UQ_whdlogs_1 UNIQUE (subscriptionId, eventRowId),
    CONSTRAINT CK_whdlogs_1 CHECK (attemptCount >= 0),
    INDEX IDX_whdlogs_1 (status, nextAttemptAt),
    INDEX IDX_whdlogs_2 (status, leaseUntil),
    INDEX IDX_whdlogs_3 (subscriptionId, createdOn),
    INDEX IDX_whdlogs_4 (eventRowId),
    INDEX IDX_whdlogs_5 (status, completedOn)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';
