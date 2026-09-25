-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade Script from 109 to 110
-- *********************************************************************
UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
CREATE TABLE peers (
    peerId              VARCHAR(256)  NOT NULL,
    url                 VARCHAR(1024) NOT NULL,
    name                VARCHAR(512)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    description         VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    enabled             BOOLEAN       NOT NULL DEFAULT TRUE,
    credentialSecretRef VARCHAR(256),
    PRIMARY KEY (peerId)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
CREATE INDEX IDX_peers_1 ON peers (enabled);
