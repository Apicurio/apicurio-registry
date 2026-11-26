-- *********************************************************************
-- DDL for the Apicurio Registry - Database: postgresql
-- Upgrade Script from 115 to 116
-- *********************************************************************
-- Re-index the structured content of Agent Cards, so the kid each signature claims (kind 'signer')
-- is indexed for Agent Cards stored before it was extracted. No schema change.

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.AgentCardStructuredContentUpgrader;

UPDATE apicurio SET propValue = 116 WHERE propName = 'db_version';
