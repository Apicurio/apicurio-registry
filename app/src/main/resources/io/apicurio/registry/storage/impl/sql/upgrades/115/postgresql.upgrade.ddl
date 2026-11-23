-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 114 to 115
-- *********************************************************************
-- Replace the sequences table (row-locked until commit) with native ID sequences.
CREATE SEQUENCE IF NOT EXISTS globalid_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS contentid_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS commentid_seq START WITH 1 INCREMENT BY 1;
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.PostgreSQLNativeSequencesUpgrader;
-- Dropping the legacy table makes pre-115 replicas fail loudly instead of issuing duplicate IDs.
DROP TABLE IF EXISTS sequences;
UPDATE apicurio SET propValue = 115 WHERE propName = 'db_version';
