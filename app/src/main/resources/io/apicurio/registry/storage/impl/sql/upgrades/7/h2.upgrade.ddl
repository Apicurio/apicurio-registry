-- *********************************************************************
-- DDL for the Apicurio Registry - Database: H2
-- Upgrades the DB schema from version 6 to version 7.
-- *********************************************************************

UPDATE apicurio SET prop_value = 7 WHERE prop_name = 'db_version';

CREATE TABLE config (tenantId VARCHAR(128) NOT NULL, pname VARCHAR(255) NOT NULL, pvalue VARCHAR(1024), modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (tenantId, pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);
