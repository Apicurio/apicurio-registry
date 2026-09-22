-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 109 to 110
-- *********************************************************************
UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
CREATE TABLE peers (peerId VARCHAR(256) NOT NULL, url VARCHAR(1024) NOT NULL, name VARCHAR(512), description VARCHAR(1024), enabled BOOLEAN NOT NULL DEFAULT TRUE, credentialSecretRef VARCHAR(256));
ALTER TABLE peers ADD PRIMARY KEY (peerId);
CREATE INDEX IDX_peers_1 ON peers(enabled);
