-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL 5.7+
-- Upgrade Script from 101 to 102
-- *********************************************************************

UPDATE apicurio SET propValue = 102 WHERE propName = 'db_version';

-- Create the new content_hashes table
CREATE TABLE content_hashes (
    contentId BIGINT       NOT NULL,
    hashType  VARCHAR(64)  NOT NULL,
    hashValue VARCHAR(128) NOT NULL,
    createdOn TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
ALTER TABLE content_hashes ADD PRIMARY KEY (contentId, hashType);
ALTER TABLE content_hashes ADD CONSTRAINT FK_content_hashes_1 FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE;
CREATE INDEX IDX_content_hashes_1 ON content_hashes (hashValue);
CREATE INDEX IDX_content_hashes_2 ON content_hashes (hashType, hashValue);

-- Migrate existing hash data from content table to content_hashes table
-- Insert all hash types, including content-sha256 (which is also stored in content.contentHash)
INSERT INTO content_hashes (contentId, hashType, hashValue, createdOn)
SELECT contentId, 'content-sha256', contentHash, CURRENT_TIMESTAMP FROM content;

INSERT INTO content_hashes (contentId, hashType, hashValue, createdOn)
SELECT contentId, 'canonical-sha256', canonicalHash, CURRENT_TIMESTAMP FROM content;

-- Drop old canonical hash index and column from content table
-- NOTE: We KEEP the contentHash column and UQ_content_1 constraint for the hybrid approach
DROP INDEX IDX_content_2 ON content;
ALTER TABLE content DROP COLUMN canonicalHash;
