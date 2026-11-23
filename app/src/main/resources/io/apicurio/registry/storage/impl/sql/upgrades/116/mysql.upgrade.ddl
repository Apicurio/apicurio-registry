-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 115 to 116
-- *********************************************************************
-- Add webhook tables for outbound event subscriptions.
-- MySQL auto-commits each DDL statement; every CREATE TABLE uses IF NOT EXISTS so that
-- a re-run after a partial failure does not fail on "table already exists".

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
    PRIMARY KEY (subscriptionId)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
-- serves: SELECT ... WHERE enabled = TRUE AND deletedOn IS NULL (recorder hot path)
CREATE INDEX IDX_whsubs_1 ON webhook_subscriptions(enabled, deletedOn);
CREATE INDEX IDX_whsubs_2 ON webhook_subscriptions(createdOn);

CREATE TABLE IF NOT EXISTS webhook_events (
    eventRowId   VARCHAR(36)  NOT NULL,
    source       TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    eventId      TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    identityHash VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    eventType    VARCHAR(256) NOT NULL,
    payload      LONGBLOB     NOT NULL,
    createdOn    BIGINT       NOT NULL,
    PRIMARY KEY (eventRowId)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
ALTER TABLE webhook_events ADD CONSTRAINT UQ_whevents_1 UNIQUE (identityHash);
CREATE INDEX IDX_whevents_1 ON webhook_events(createdOn);

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
    PRIMARY KEY (deliveryId)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_2 FOREIGN KEY (eventRowId) REFERENCES webhook_events(eventRowId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT UQ_whdlogs_1 UNIQUE (subscriptionId, eventRowId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT CK_whdlogs_1 CHECK (attemptCount >= 0);
CREATE INDEX IDX_whdlogs_1 ON webhook_delivery_logs(status, nextAttemptAt);
CREATE INDEX IDX_whdlogs_2 ON webhook_delivery_logs(status, leaseUntil);
CREATE INDEX IDX_whdlogs_3 ON webhook_delivery_logs(subscriptionId, createdOn);
CREATE INDEX IDX_whdlogs_4 ON webhook_delivery_logs(eventRowId);
CREATE INDEX IDX_whdlogs_5 ON webhook_delivery_logs(status, completedOn);

UPDATE apicurio SET propValue = 116 WHERE propName = 'db_version';
