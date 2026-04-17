-- *********************************************************************
-- DDL for the Apicurio Registry - Database: MySQL
-- Upgrade Script from 102 to 103
-- *********************************************************************

UPDATE apicurio SET propValue = 103 WHERE propName = 'db_version';

CREATE TABLE contract_rules (
    ruleId        BIGINT       NOT NULL AUTO_INCREMENT,
    groupId       VARCHAR(512) NOT NULL,
    artifactId    VARCHAR(512) NOT NULL,
    globalId      BIGINT,
    ruleCategory  VARCHAR(32)  NOT NULL,
    orderIndex    INT          NOT NULL,
    ruleName      VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    kind          VARCHAR(32)  NOT NULL,
    ruleType      VARCHAR(256) NOT NULL,
    mode          VARCHAR(32)  NOT NULL,
    expr          TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    params        TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    tags          TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    onSuccess     VARCHAR(32),
    onFailure     VARCHAR(32),
    disabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (ruleId)
) DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;
ALTER TABLE contract_rules ADD CONSTRAINT FK_contract_rules_1 FOREIGN KEY (globalId) REFERENCES versions(globalId) ON DELETE CASCADE;
CREATE INDEX IDX_contract_rules_1 ON contract_rules(groupId, artifactId);
CREATE INDEX IDX_contract_rules_2 ON contract_rules(globalId);
