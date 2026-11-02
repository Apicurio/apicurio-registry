-- *********************************************************************
-- DDL for the Apicurio Registry - Database: h2
-- Upgrade Script from 110 to 111
-- *********************************************************************

CREATE TABLE IF NOT EXISTS peers (peerId VARCHAR(256) NOT NULL, url VARCHAR(1024) NOT NULL, name VARCHAR(512), description VARCHAR(1024), enabled BOOLEAN NOT NULL DEFAULT TRUE, credentialSecretRef VARCHAR(256), PRIMARY KEY (peerId));
CREATE INDEX IF NOT EXISTS IDX_peers_1 ON peers(enabled);

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';
