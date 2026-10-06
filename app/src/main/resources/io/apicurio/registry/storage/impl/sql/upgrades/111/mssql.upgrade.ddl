-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 110 to 111
-- *********************************************************************

CREATE TABLE webhook_subscriptions (
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
  modifiedOn         BIGINT         NOT NULL
);
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);
-- serves: SELECT ... WHERE enabled = TRUE AND deletedOn IS NULL (recorder hot path)
CREATE INDEX IDX_whsubs_1 ON webhook_subscriptions(enabled, deletedOn);
CREATE INDEX IDX_whsubs_2 ON webhook_subscriptions(createdOn);

CREATE TABLE webhook_events (
  eventRowId   NVARCHAR(36)  NOT NULL,
  source       NVARCHAR(MAX) NOT NULL,
  eventId      NVARCHAR(MAX) NOT NULL,
  identityHash NVARCHAR(64)  NOT NULL,
  eventType    NVARCHAR(256) NOT NULL,
  payload      VARBINARY(MAX) NOT NULL,
  createdOn    BIGINT        NOT NULL
);
ALTER TABLE webhook_events ADD PRIMARY KEY (eventRowId);
ALTER TABLE webhook_events ADD CONSTRAINT UQ_whevents_1 UNIQUE (identityHash);
CREATE INDEX IDX_whevents_1 ON webhook_events(createdOn);

CREATE TABLE webhook_delivery_logs (
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
  completedOn    BIGINT
);
ALTER TABLE webhook_delivery_logs ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_1
  FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_2
  FOREIGN KEY (eventRowId) REFERENCES webhook_events(eventRowId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT UQ_whdlogs_1 UNIQUE (subscriptionId, eventRowId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT CK_whdlogs_1 CHECK (attemptCount >= 0);
CREATE INDEX IDX_whdlogs_1 ON webhook_delivery_logs(status, nextAttemptAt);
CREATE INDEX IDX_whdlogs_2 ON webhook_delivery_logs(status, leaseUntil);
CREATE INDEX IDX_whdlogs_3 ON webhook_delivery_logs(subscriptionId, createdOn);
CREATE INDEX IDX_whdlogs_4 ON webhook_delivery_logs(eventRowId);
CREATE INDEX IDX_whdlogs_5 ON webhook_delivery_logs(status, completedOn);

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';
