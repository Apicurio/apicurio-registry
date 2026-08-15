-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 109 to 110
-- *********************************************************************

UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';

CREATE TABLE webhook_subscriptions (subscriptionId NVARCHAR(128) NOT NULL, name NVARCHAR(512), endpointUrl NVARCHAR(1024) NOT NULL, eventTypes NVARCHAR(MAX) NOT NULL, groupFilter NVARCHAR(512), artifactIdFilter NVARCHAR(512), enabled BIT NOT NULL DEFAULT 1, secret NVARCHAR(512), createdBy NVARCHAR(256), createdOn DATETIME2(6) NOT NULL, modifiedOn DATETIME2(6) NOT NULL);
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);

CREATE TABLE webhook_delivery_logs (deliveryId NVARCHAR(128) NOT NULL, subscriptionId NVARCHAR(128) NOT NULL, eventId NVARCHAR(256) NOT NULL, eventType NVARCHAR(128) NOT NULL, status NVARCHAR(32) NOT NULL, attemptCount INT NOT NULL DEFAULT 0, lastAttemptAt DATETIME2(6), nextRetryAt DATETIME2(6), errorMessage NVARCHAR(MAX), httpStatusCode INT, lockedBy NVARCHAR(256), leaseUntil DATETIME2(6), createdOn DATETIME2(6) NOT NULL);
ALTER TABLE webhook_delivery_logs ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT UQ_webhook_delivery_logs_1 UNIQUE (subscriptionId, eventId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_webhook_delivery_logs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE INDEX IDX_webhook_delivery_logs_1 ON webhook_delivery_logs(status, nextRetryAt);
CREATE INDEX IDX_webhook_delivery_logs_2 ON webhook_delivery_logs(eventId);
CREATE INDEX IDX_webhook_delivery_logs_3 ON webhook_delivery_logs(createdOn);
