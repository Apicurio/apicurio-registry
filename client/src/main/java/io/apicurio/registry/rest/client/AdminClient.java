package io.apicurio.registry.rest.client;

import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

public interface AdminClient extends Closeable {

    List<RuleType> listGlobalRules();

    void deleteAllGlobalRules();

    Rule getGlobalRuleConfig(RuleType rule);

    void createGlobalRule(Rule data);

    Rule updateGlobalRuleConfig(RuleType rule, Rule data);

    void deleteGlobalRule(RuleType rule);

    List<NamedLogConfiguration> listLogConfigurations();

    NamedLogConfiguration getLogConfiguration(String logger);

    NamedLogConfiguration removeLogConfiguration(String logger);

    NamedLogConfiguration setLogConfiguration(String log, LogConfiguration logConfiguration);

    List<RoleMapping> listRoleMappings();

    void createRoleMapping(RoleMapping data);

    RoleMapping getRoleMapping(String principalId);

    void updateRoleMapping(String principalId, RoleType role);

    void deleteRoleMapping(String principalId);

    InputStream exportData();

    void importData(InputStream data);
}
