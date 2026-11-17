-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 110 to 111
-- *********************************************************************

CREATE TABLE peers (peerId VARCHAR(256) NOT NULL, url VARCHAR(1024) NOT NULL, name VARCHAR(512), description VARCHAR(1024), enabled BOOLEAN NOT NULL DEFAULT TRUE, credentialSecretRef VARCHAR(256));
ALTER TABLE peers ADD PRIMARY KEY (peerId);
CREATE INDEX IDX_peers_1 ON peers(enabled);

UPDATE apicurio SET propValue = 111 WHERE propName = 'db_version';
