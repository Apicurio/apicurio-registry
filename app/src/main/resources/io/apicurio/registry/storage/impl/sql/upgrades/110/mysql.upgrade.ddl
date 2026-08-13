-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade Script from 109 to 110
-- *********************************************************************
UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
CREATE TABLE webhook_subscriptions (subscriptionId VARCHAR(128) NOT NULL, endpointUrl VARCHAR(1024) NOT NULL, eventTypes TEXT, groupFilter VARCHAR(512), artifactFilter VARCHAR(512), authType VARCHAR(32) NOT NULL DEFAULT 'NONE', authConfig TEXT, isEnabled BOOLEAN NOT NULL DEFAULT TRUE, owner VARCHAR(256), createdOn TIMESTAMP NOT NULL, modifiedBy VARCHAR(256), modifiedOn TIMESTAMP NULL);
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);
CREATE INDEX IDX_whsubs_1 ON webhook_subscriptions(isEnabled);
CREATE INDEX IDX_whsubs_2 ON webhook_subscriptions(createdOn);
CREATE TABLE webhook_delivery_logs (deliveryId BIGINT AUTO_INCREMENT NOT NULL, subscriptionId VARCHAR(128) NOT NULL, eventId VARCHAR(128) NOT NULL, attemptCount INT NOT NULL DEFAULT 0, lastAttemptOn TIMESTAMP NULL, status VARCHAR(32) NOT NULL, responseCode INT, createdOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE webhook_delivery_logs ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_whdlogs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE INDEX IDX_whdlogs_1 ON webhook_delivery_logs(subscriptionId, status);
CREATE INDEX IDX_whdlogs_2 ON webhook_delivery_logs(lastAttemptOn);
CREATE INDEX IDX_whdlogs_3 ON webhook_delivery_logs(status);