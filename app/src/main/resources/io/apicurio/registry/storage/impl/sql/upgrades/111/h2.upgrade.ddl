-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 110 to 111
-- *********************************************************************

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';

CREATE TABLE webhook_subscriptions (subscriptionId VARCHAR(128) NOT NULL, name VARCHAR(512), endpointUrl VARCHAR(1024) NOT NULL, eventTypes TEXT NOT NULL, groupFilter VARCHAR(512), artifactIdFilter VARCHAR(512), enabled BOOLEAN NOT NULL DEFAULT TRUE, secret VARCHAR(512), createdBy VARCHAR(256), createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL, modifiedOn TIMESTAMP WITHOUT TIME ZONE NOT NULL);
ALTER TABLE webhook_subscriptions ADD PRIMARY KEY (subscriptionId);
CREATE INDEX IDX_webhook_subs_1 ON webhook_subscriptions(enabled);
CREATE INDEX IDX_webhook_subs_2 ON webhook_subscriptions(createdOn);

CREATE TABLE webhook_delivery_logs (deliveryId VARCHAR(128) NOT NULL, subscriptionId VARCHAR(128) NOT NULL, eventId VARCHAR(256) NOT NULL, eventType VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, attemptCount INT NOT NULL DEFAULT 0, lastAttemptAt TIMESTAMP WITHOUT TIME ZONE, nextRetryAt TIMESTAMP WITHOUT TIME ZONE, errorMessage TEXT, httpStatusCode INT, createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL);
ALTER TABLE webhook_delivery_logs ADD PRIMARY KEY (deliveryId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT UQ_webhook_delivery_logs_1 UNIQUE (subscriptionId, eventId);
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_webhook_delivery_logs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE INDEX IDX_webhook_delivery_logs_1 ON webhook_delivery_logs(status, nextRetryAt);
CREATE INDEX IDX_webhook_delivery_logs_2 ON webhook_delivery_logs(eventId);
