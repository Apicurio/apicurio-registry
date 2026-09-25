-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 109 to 110
-- *********************************************************************
UPDATE apicurio SET propValue = 110 WHERE propName = 'db_version';
CREATE TABLE peers (peerId NVARCHAR(256) NOT NULL, url NVARCHAR(1024) NOT NULL, name NVARCHAR(512), description NVARCHAR(1024), enabled BIT NOT NULL DEFAULT 1, credentialSecretRef NVARCHAR(256));
ALTER TABLE peers ADD PRIMARY KEY (peerId);
CREATE INDEX IDX_peers_1 ON peers(enabled);
