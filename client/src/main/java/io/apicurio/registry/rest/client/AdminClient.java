/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest.client;

import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;

import java.io.Closeable;
import java.io.InputStream;
import java.util.List;

/**
 * @author Jonathan Hughes 'jonathan.hughes@ibm.com'
 */

public interface AdminClient extends Closeable {

    void createGlobalRule(Rule data);

    List<RuleType> listGlobalRules();

    void deleteGlobalRule(RuleType rule);

    void deleteAllGlobalRules();

    Rule getGlobalRuleConfig(RuleType rule);

    Rule updateGlobalRuleConfig(RuleType rule, Rule data);

    NamedLogConfiguration setLogConfiguration(String log, LogConfiguration logConfiguration);

    NamedLogConfiguration getLogConfiguration(String logger);

    List<NamedLogConfiguration> listLogConfigurations();

    NamedLogConfiguration removeLogConfiguration(String logger);

    void createRoleMapping(RoleMapping data);

    RoleMapping getRoleMapping(String principalId);

    List<RoleMapping> listRoleMappings();

    void updateRoleMapping(String principalId, RoleType role);

    void deleteRoleMapping(String principalId);

    InputStream exportData();

    void importData(InputStream data);

    void importData(InputStream data, boolean preserveGlobalIds, boolean preserveContentIds);
}
