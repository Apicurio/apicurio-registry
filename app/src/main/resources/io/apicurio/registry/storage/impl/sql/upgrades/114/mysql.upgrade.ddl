-- *********************************************************************
-- DDL for the Apicurio Registry - Database: mysql
-- Upgrade Script from 113 to 114
-- *********************************************************************
-- Names, labels and principals are user text; they were created ASCII and refused anything else.
-- Principals keep a case-insensitive comparison, as with the ASCII collation, but now tell accents
-- apart, so 'jose' never matches the role mappings or artifacts of 'josé'.
ALTER TABLE `groups` MODIFY owner VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci, MODIFY modifiedBy VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;
ALTER TABLE artifacts MODIFY owner VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci, MODIFY modifiedBy VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci, MODIFY name VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY labels TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE versions MODIFY name VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, MODIFY owner VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci, MODIFY modifiedBy VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci, MODIFY labels TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE branches MODIFY owner VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci, MODIFY modifiedBy VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;
ALTER TABLE version_comments MODIFY owner VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;
ALTER TABLE acls MODIFY principalId VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL;
UPDATE apicurio SET propValue = 114 WHERE propName = 'db_version';
