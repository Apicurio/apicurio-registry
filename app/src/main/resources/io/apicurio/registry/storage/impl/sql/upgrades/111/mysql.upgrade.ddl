-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 110 to 111
-- *********************************************************************

CREATE TABLE IF NOT EXISTS peers (
    peerId              VARCHAR(256)  NOT NULL,
    url                 VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    name                VARCHAR(512)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    description         VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    enabled             BOOLEAN       NOT NULL DEFAULT TRUE,
    credentialSecretRef VARCHAR(256),
    PRIMARY KEY (peerId),
    INDEX IDX_peers_1 (enabled)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';
