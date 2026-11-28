-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 115 to 116
-- *********************************************************************
-- Add webhook tables for outbound event subscriptions.
-- OBJECT_ID guards on tables and sys.indexes guards on indexes ensure the script
-- can be safely re-run if a previous attempt was interrupted.

IF OBJECT_ID(N'webhook_subscriptions', N'U') IS NULL CREATE TABLE webhook_subscriptions (subscriptionId NVARCHAR(36) NOT NULL, name NVARCHAR(512), ownerId NVARCHAR(256) NOT NULL, endpointUrl NVARCHAR(2048) NOT NULL, eventTypes NVARCHAR(MAX) NOT NULL, groupFilter NVARCHAR(512), artifactIdFilter NVARCHAR(512), artifactTypeFilter NVARCHAR(32), enabled BIT NOT NULL, deletedOn BIGINT, revision BIGINT NOT NULL, signingSecretRef NVARCHAR(512), createdOn BIGINT NOT NULL, modifiedOn BIGINT NOT NULL, PRIMARY KEY (subscriptionId));
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whsubs_1' AND object_id = OBJECT_ID(N'webhook_subscriptions')) CREATE INDEX IDX_whsubs_1 ON webhook_subscriptions(enabled, deletedOn);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whsubs_2' AND object_id = OBJECT_ID(N'webhook_subscriptions')) CREATE INDEX IDX_whsubs_2 ON webhook_subscriptions(createdOn);

IF OBJECT_ID(N'webhook_events', N'U') IS NULL CREATE TABLE webhook_events (eventRowId NVARCHAR(36) NOT NULL, source NVARCHAR(MAX) NOT NULL, eventId NVARCHAR(MAX) NOT NULL, identityHash NVARCHAR(64) NOT NULL, eventType NVARCHAR(256) NOT NULL, payload VARBINARY(MAX) NOT NULL, createdOn BIGINT NOT NULL, PRIMARY KEY (eventRowId));
IF OBJECT_ID(N'UQ_whevents_1', N'UQ') IS NULL ALTER TABLE webhook_events ADD CONSTRAINT UQ_whevents_1 UNIQUE (identityHash);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whevents_1' AND object_id = OBJECT_ID(N'webhook_events')) CREATE INDEX IDX_whevents_1 ON webhook_events(createdOn);

IF OBJECT_ID(N'webhook_delivery_logs', N'U') IS NULL CREATE TABLE webhook_delivery_logs (deliveryId NVARCHAR(36) NOT NULL, subscriptionId NVARCHAR(36) NOT NULL, eventRowId NVARCHAR(36) NOT NULL, status NVARCHAR(16) NOT NULL, attemptCount INT NOT NULL DEFAULT 0, nextAttemptAt BIGINT, claimToken NVARCHAR(36), leaseUntil BIGINT, lastAttemptAt BIGINT, httpStatusCode INT, errorCode NVARCHAR(64), createdOn BIGINT NOT NULL, updatedOn BIGINT NOT NULL, completedOn BIGINT, PRIMARY KEY (deliveryId));
IF OBJECT_ID(N'FK_whdlogs_1', N'F') IS NULL ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId);
IF OBJECT_ID(N'FK_whdlogs_2', N'F') IS NULL ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_2 FOREIGN KEY (eventRowId) REFERENCES webhook_events(eventRowId);
IF OBJECT_ID(N'UQ_whdlogs_1', N'UQ') IS NULL ALTER TABLE webhook_delivery_logs ADD CONSTRAINT UQ_whdlogs_1 UNIQUE (subscriptionId, eventRowId);
IF OBJECT_ID(N'CK_whdlogs_1', N'C') IS NULL ALTER TABLE webhook_delivery_logs ADD CONSTRAINT CK_whdlogs_1 CHECK (attemptCount >= 0);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whdlogs_1' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_1 ON webhook_delivery_logs(status, nextAttemptAt);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whdlogs_2' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_2 ON webhook_delivery_logs(status, leaseUntil);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whdlogs_3' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_3 ON webhook_delivery_logs(subscriptionId, createdOn);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whdlogs_4' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_4 ON webhook_delivery_logs(eventRowId);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IDX_whdlogs_5' AND object_id = OBJECT_ID(N'webhook_delivery_logs')) CREATE INDEX IDX_whdlogs_5 ON webhook_delivery_logs(status, completedOn);

UPDATE apicurio SET propValue = 116 WHERE propName = 'db_version';
