-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrade Script from 110 to 111
--
-- Retry-safe: every statement is idempotent.
-- CREATE TABLE IF NOT EXISTS is a no-op when the table was already
-- committed by a prior partial run.  CREATE INDEX IF NOT EXISTS does
-- the same for indexes.  Constraints are inlined in CREATE TABLE so
-- they are created atomically with the table and never need to be
-- re-applied on retry.  The version marker is written last so a
-- partial failure leaves the version at 110 and the upgrade runs
-- again in full on the next application start.
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
  modifiedOn         BIGINT        NOT NULL,
  PRIMARY KEY (subscriptionId)
);
CREATE INDEX IF NOT EXISTS IDX_whsubs_1 ON webhook_subscriptions(enabled, deletedOn);
CREATE INDEX IF NOT EXISTS IDX_whsubs_2 ON webhook_subscriptions(createdOn);

CREATE TABLE IF NOT EXISTS webhook_events (
  eventRowId   VARCHAR(36)  NOT NULL,
  source       TEXT         NOT NULL,
  eventId      TEXT         NOT NULL,
  identityHash VARCHAR(64)  NOT NULL,
  eventType    VARCHAR(256) NOT NULL,
  payload      BYTEA        NOT NULL,
  createdOn    BIGINT       NOT NULL,
  PRIMARY KEY (eventRowId),
  CONSTRAINT UQ_whevents_1 UNIQUE (identityHash)
);
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
  completedOn    BIGINT,
  PRIMARY KEY (deliveryId),
  CONSTRAINT FK_whdlogs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId),
  CONSTRAINT FK_whdlogs_2 FOREIGN KEY (eventRowId) REFERENCES webhook_events(eventRowId),
  CONSTRAINT UQ_whdlogs_1 UNIQUE (subscriptionId, eventRowId),
  CONSTRAINT CK_whdlogs_1 CHECK (attemptCount >= 0)
);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_1 ON webhook_delivery_logs(status, nextAttemptAt);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_2 ON webhook_delivery_logs(status, leaseUntil);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_3 ON webhook_delivery_logs(subscriptionId, createdOn);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_4 ON webhook_delivery_logs(eventRowId);
CREATE INDEX IF NOT EXISTS IDX_whdlogs_5 ON webhook_delivery_logs(status, completedOn);

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';
