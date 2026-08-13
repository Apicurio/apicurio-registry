-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MS SQL Server
-- Upgrade Script from 109 to 110
-- *********************************************************************
UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
CREATE TABLE webhook_subscriptions (subscriptionId NVARCHAR(128) NOT NULL, endpointUrl NVARCHAR(1024) NOT NULL, eventTypes TEXT, groupFilter NVARCHAR(512), artifactFilter NVARCHAR(512), authType NVARCHAR(32) NOT NULL DEFAULT 'NONE', authConfig TEXT, isEnabled BIT NOT NULL DEFAULT 1, owner NVARCHAR(256), createdOn DATETIME2 NOT NULL, modifiedBy NVARCHAR(256), modifiedOn DATETIME2);
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);
CREATE INDEX IDX_whsubs_1 ON webhook_subscriptions(isEnabled);
CREATE INDEX IDX_whsubs_2 ON webhook_subscriptions(createdOn);
CREATE TABLE webhook_delivery_logs (deliveryId BIGINT IDENTITY(1,1) NOT NULL, subscriptionId NVARCHAR(128) NOT NULL, eventId NVARCHAR(128) NOT NULL, attemptCount INT NOT NULL DEFAULT 0, lastAttemptOn DATETIME2, status NVARCHAR(32) NOT NULL, responseCode INT, createdOn DATETIME2 NOT NULL DEFAULT GETDATE());
ALTER TABLE webhook_delivery_logs ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE INDEX IDX_whdlogs_1 ON webhook_delivery_logs(subscriptionId, status);
CREATE INDEX IDX_whdlogs_2 ON webhook_delivery_logs(lastAttemptOn);
CREATE INDEX IDX_whdlogs_3 ON webhook_delivery_logs(status);