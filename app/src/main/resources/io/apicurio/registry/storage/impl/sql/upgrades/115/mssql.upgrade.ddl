-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mssql
-- Upgrade Script from 114 to 115
-- *********************************************************************
-- Replace the sequences table (row-locked until commit) with native ID sequences.
IF OBJECT_ID(N'globalid_seq', N'SO') IS NULL CREATE SEQUENCE globalid_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CACHE;
IF OBJECT_ID(N'contentid_seq', N'SO') IS NULL CREATE SEQUENCE contentid_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CACHE;
IF OBJECT_ID(N'commentid_seq', N'SO') IS NULL CREATE SEQUENCE commentid_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CACHE;
UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.SQLServerNativeSequencesUpgrader;
-- Dropping the legacy table makes pre-115 replicas fail loudly instead of issuing duplicate IDs.
IF OBJECT_ID(N'sequences', N'U') IS NOT NULL DROP TABLE sequences;
UPDATE apicurio SET propValue = 115 WHERE propName = 'db_version';
