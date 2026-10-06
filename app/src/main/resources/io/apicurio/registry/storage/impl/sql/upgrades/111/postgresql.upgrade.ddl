-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrade Script from 110 to 111
--
-- PostgreSQL DDL runs inside a transaction; a failure rolls back the
-- entire script, so partial-upgrade retry is handled automatically.
-- IF NOT EXISTS guards are included for consistency with the other
-- dialects and to make manual re-runs safe.
-- *********************************************************************

-- serves: SELECT ... WHERE enabled = TRUE AND deletedOn IS NULL (recorder hot path)
CREATE TABLE IF NOT EXISTS webhook_subscriptions (
  subscriptionId     VARCHAR(36)   NOT NULL,
  name               VARCHAR(512),
  ownerId            VARCHAR(256)  NOT NULL,
  endpointUrl        VARCHAR(2048) NOT NULL,
  eventTypes         TEXT          NOT NULL,
  groupFilter        VARCHAR(512),
  artifactIdFilter   VARCHAR(512),
  artifactTypeFilter VARCHAR(32),
  enabled            BOOLEAN       NOT NULL,
  deletedOn          BIGINT,
  revision           BIGINT        NOT NULL,
  signingSecretRef   VARCHAR(512),
  createdOn          BIGINT        NOT NULL,
  modifiedOn         BIGINT        NOT NULL
);
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);
CREATE INDEX IF NOT EXISTS IDX_whsubs_1 ON webhook_subscriptions(enabled, deletedOn);
CREATE INDEX IF NOT EXISTS IDX_whsubs_2 ON webhook_subscriptions(createdOn);

CREATE TABLE IF NOT EXISTS webhook_events (
  eventRowId   VARCHAR(36)  NOT NULL,
  source       TEXT         NOT NULL,
  eventId      TEXT         NOT NULL,
  identityHash VARCHAR(64)  NOT NULL,
  eventType    VARCHAR(256) NOT NULL,
  payload      BYTEA        NOT NULL,
  createdOn    BIGINT       NOT NULL
);
ALTER TABLE webhook_events ADD PRIMARY KEY (eventRowId);
ALTER TABLE webhook_events ADD CONSTRAINT UQ_whevents_1 UNIQUE (identityHash);
CREATE INDEX IF NOT EXISTS IDX_whevents_1 ON webhook_events(createdOn);

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
  completedOn    BIGINT
);
ALTER TABLE webhook_delivery_logs ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_1
  FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_2
  FOREIGN KEY (eventRowId) REFERENCES webhook_events(eventRowId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT UQ_whdlogs_1 UNIQUE (subscriptionId, eventRowId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT CK_whdlogs_1 CHECK (attemptCount >= 0);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_1 ON webhook_delivery_logs(status, nextAttemptAt);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_2 ON webhook_delivery_logs(status, leaseUntil);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_3 ON webhook_delivery_logs(subscriptionId, createdOn);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_4 ON webhook_delivery_logs(eventRowId);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_5 ON webhook_delivery_logs(status, completedOn);

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';
