-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 100 to 101
-- *********************************************************************

UPDATE apicurio SET prop_value = 101 WHERE prop_name = 'db_version';

CREATE TABLE outbox (id VARCHAR(128) NOT NULL, aggregatetype VARCHAR(255) NOT NULL, aggregateid VARCHAR(255) NOT NULL, type VARCHAR(255) NOT NULL, payload JSONB NOT NULL);
ALTER TABLE outbox ADD PRIMARY KEY (id);
