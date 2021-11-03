-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 4 to version 5.
-- *********************************************************************

UPDATE apicurio SET prop_value = 5 WHERE prop_name = 'db_version';

CREATE TABLE config (tenantId VARCHAR(128) NOT NULL, pname VARCHAR(255) NOT NULL, ptype VARCHAR(255) NOT NULL, pvalue VARCHAR(1024), modifiedOn BIGINT NOT NULL);
ALTER TABLE config ADD PRIMARY KEY (tenantId, pname);
CREATE INDEX IDX_config_1 ON config(modifiedOn);
