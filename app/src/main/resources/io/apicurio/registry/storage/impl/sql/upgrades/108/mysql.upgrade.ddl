-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 107 to 108
-- *********************************************************************

UPDATE apicurio SET propValue = 108 WHERE propName = 'db_version';

CREATE TABLE webhook_subscriptions (
    subscriptionId   VARCHAR(128)  NOT NULL,
    name             VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    endpointUrl      VARCHAR(1024) NOT NULL,
    eventTypes       TEXT          NOT NULL,
    groupFilter      VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    artifactIdFilter VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    secret           VARCHAR(512),
    createdBy        VARCHAR(256),
    createdOn        TIMESTAMP     NOT NULL,
    modifiedOn       TIMESTAMP     NOT NULL,
    PRIMARY KEY (subscriptionId)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
CREATE INDEX IDX_webhook_subs_1 ON webhook_subscriptions(enabled);
CREATE INDEX IDX_webhook_subs_2 ON webhook_subscriptions(createdOn);

CREATE TABLE webhook_delivery_logs (
    deliveryId      VARCHAR(128) NOT NULL,
    subscriptionId  VARCHAR(128) NOT NULL,
    eventId         VARCHAR(256) NOT NULL,
    eventType       VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    attemptCount    INT          NOT NULL DEFAULT 0,
    lastAttemptAt   TIMESTAMP    NULL,
    nextRetryAt     TIMESTAMP    NULL,
    errorMessage    TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    httpStatusCode  INT,
    createdOn       TIMESTAMP    NOT NULL,
    PRIMARY KEY (deliveryId),
    CONSTRAINT UQ_webhook_delivery_logs_1 UNIQUE (subscriptionId, eventId)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
ALTER TABLE webhook_delivery_logs ADD CONSTRAINT FK_webhook_delivery_logs_1 FOREIGN KEY (subscriptionId) REFERENCES webhook_subscriptions(subscriptionId) ON DELETE CASCADE;
CREATE INDEX IDX_webhook_delivery_logs_1 ON webhook_delivery_logs(subscriptionId);
CREATE INDEX IDX_webhook_delivery_logs_2 ON webhook_delivery_logs(status, nextRetryAt);
CREATE INDEX IDX_webhook_delivery_logs_3 ON webhook_delivery_logs(eventId);
