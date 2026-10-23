-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 111 to 112
--
-- Retry-safe: SQL Server DDL runs inside an explicit JDBC transaction in
-- the production upgrader; a failure rolls back the whole step and leaves
-- db_version at 111 for a clean retry.  IF NOT EXISTS guards are added so
-- that the script is also safe when re-run outside a transaction (e.g.
-- during manual recovery or a mid-script failure on an older driver).
--
-- CREATE TABLE: guarded by IF OBJECT_ID IS NULL.  All constraints (PK,
-- FK, UQ, CK) are inlined into CREATE TABLE so that the entire table
-- definition is atomic; no separate ALTER TABLE is required.
-- CREATE INDEX: guarded by a sys.indexes name lookup.
-- The version marker is written last so a partial failure leaves the
-- version at 111 and the full upgrade is retried on the next start.
-- *********************************************************************

IF OBJECT_ID(N'webhook_subscriptions', N'U') IS NULL CREATE TABLE webhook_subscriptions (
  subscriptionId     NVARCHAR(36)   NOT NULL,
  name               NVARCHAR(512),
  ownerId            NVARCHAR(256)  NOT NULL,
  endpointUrl        NVARCHAR(2048) NOT NULL,
  eventTypes         NVARCHAR(MAX)  NOT NULL,
  groupFilter        NVARCHAR(512),
  artifactIdFilter   NVARCHAR(512),
  artifactTypeFilter NVARCHAR(32),
  enabled            BIT            NOT NULL,
  deletedOn          BIGINT,
  revision           BIGINT         NOT NULL,
  signingSecretRef   NVARCHAR(512),
  createdOn          BIGINT         NOT NULL,
  modifiedOn         BIGINT         NOT NULL,
  PRIMARY KEY (subscriptionId)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whsubs_1' AND object_id = OBJECT_ID(N'webhook_subscriptions')) CREATE INDEX IDX_whsubs_1 ON webhook_subscriptions(enabled, deletedOn);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whsubs_2' AND object_id = OBJECT_ID(N'webhook_subscriptions')) CREATE INDEX IDX_whsubs_2 ON webhook_subscriptions(createdOn);

IF OBJECT_ID(N'webhook_events', N'U') IS NULL CREATE TABLE webhook_events (
  eventRowId   NVARCHAR(36)   NOT NULL,
  source       NVARCHAR(MAX)  NOT NULL,
  eventId      NVARCHAR(MAX)  NOT NULL,
  identityHash NVARCHAR(64)   NOT NULL,
  eventType    NVARCHAR(256)  NOT NULL,
  payload      VARBINARY(MAX) NOT NULL,
  createdOn    BIGINT         NOT NULL,
  PRIMARY KEY (eventRowId),
  CONSTRAINT UQ_whevents_1 UNIQUE (identityHash)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whevents_1' AND object_id = OBJECT_ID(N'webhook_events')) CREATE INDEX IDX_whevents_1 ON webhook_events(createdOn);

IF OBJECT_ID(N'webhook_delivery_logs', N'U') IS NULL CREATE TABLE webhook_delivery_logs (
  deliveryId     NVARCHAR(36) NOT NULL,
  subscriptionId NVARCHAR(36) NOT NULL,
  eventRowId     NVARCHAR(36) NOT NULL,
  status         NVARCHAR(16) NOT NULL,
  attemptCount   INT          NOT NULL DEFAULT 0,
  nextAttemptAt  BIGINT,
  claimToken     NVARCHAR(36),
  leaseUntil     BIGINT,
  lastAttemptAt  BIGINT,
  httpStatusCode INT,
  errorCode      NVARCHAR(64),
  createdOn      BIGINT       NOT NULL,
  updatedOn      BIGINT       NOT NULL,
  completedOn    BIGINT,
  PRIMARY KEY (deliveryId),
  CONSTRAINT FK_whdlogs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId),
  CONSTRAINT FK_whdlogs_2 FOREIGN KEY (eventRowId) REFERENCES webhook_events(eventRowId),
  CONSTRAINT UQ_whdlogs_1 UNIQUE (subscriptionId, eventRowId),
  CONSTRAINT CK_whdlogs_1 CHECK (attemptCount >= 0)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whdlogs_1' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_1 ON webhook_delivery_logs(status, nextAttemptAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whdlogs_2' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_2 ON webhook_delivery_logs(status, leaseUntil);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whdlogs_3' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_3 ON webhook_delivery_logs(subscriptionId, createdOn);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whdlogs_4' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_4 ON webhook_delivery_logs(eventRowId);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_whdlogs_5' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_5 ON webhook_delivery_logs(status, completedOn);

UPDATE apicurio SET propValue = 112 WHERE propName = 'db_version';
