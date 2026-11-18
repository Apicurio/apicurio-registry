-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 114 to 115
-- *********************************************************************
-- Replace the sequences table (row-locked until commit) with native ID sequences.
CREATE TABLE globalid_seq (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE contentid_seq (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE commentid_seq (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.MySQLNativeSequencesUpgrader;
-- Dropping the legacy table makes pre-115 replicas fail loudly instead of issuing duplicate IDs.
DROP TABLE sequences;
UPDATE apicurio SET propValue = 115 WHERE propName = 'db_version';
