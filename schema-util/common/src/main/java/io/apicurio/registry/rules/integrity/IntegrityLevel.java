package io.apicurio.registry.rules.integrity;

/**
 * Indicates what level of integrity should be performed by the referential integrity rule.
 */
public enum IntegrityLevel {

    NONE, REFS_EXIST, ALL_REFS_MAPPED, NO_DUPLICATES, FULL;
    
}
